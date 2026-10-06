package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.enums.FileCategoryEnums;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.query.CursorPage;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.entity.vo.FolderVO;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import com.easypan.service.FileOperationService;
import com.easypan.utils.CopyTools;
import com.easypan.utils.StringTools;
import com.easypan.utils.UploadPathValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 閺傚洣娆㈡穱鈩冧紖閹貉冨煑閸ｃ劎琚?
 */
@Component
@RestController("fileInfoController")
@RequestMapping("/file")
@Tag(name = "File Management", description = "File operations including upload, download, and management")
public class FileInfoController extends CommonFileController {

    @Resource
    private FileOperationService fileOperationService;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    /**
     * 閺嶈宓侀弶鈥叉閸掑棝銆夐弻銉嚄.
     *
     * @param session  HTTP 娴兼俺鐦?
     * @param query    閺屻儴顕楅崣鍌涙殶
     * @param category 閸掑棛琚?
     * @return 閸掑棝銆夌紒鎾寸亯
     */
    @RequestMapping("/loadDataList")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Load Data List", description = "Pagination query for file list")
    public ResponseVO<PaginationResultVO<FileInfoVO>> loadDataList(HttpSession session, FileInfoQuery query,
            String category) {
        FileCategoryEnums categoryEnum = FileCategoryEnums.getByCode(category);
        if (null != categoryEnum) {
            query.setFileCategory(categoryEnum.getCategory());
        }
        query.setUserId(getUserInfoFromSession(session).getUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        PaginationResultVO<FileInfo> result = fileInfoService.findListByPage(query);
        return getSuccessResponseVO(convert2PaginationVO(result, FileInfoVO.class));
    }

    /**
     * 濞撳憡鐖ｉ崚鍡涖€夐弻銉嚄閿涘牊鈧嗗厴娴兼ü绨?OFFSET 閸掑棝銆夐敍?
     * 闁倻鏁ゆ禍搴°亣閺佺増宓侀柌蹇撴簚閺咁垽绱濋柆鍨帳濞ｅ崬鍨庢い鍨偓褑鍏橀梻顕€顣?
     *
     * @param session  HTTP 娴兼俺鐦?
     * @param cursor   濞撳憡鐖?
     * @param pageSize 濮ｅ繘銆夋径褍鐨?
     * @return 閸掑棝銆夌紒鎾寸亯
     */
    @RequestMapping("/loadDataListCursor")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Load Data List with Cursor", description = "Cursor-based pagination for better performance")
    public ResponseVO<CursorPage<FileInfoVO>> loadDataListCursor(
            HttpSession session,
            String cursor,
            Integer pageSize) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        CursorPage<FileInfo> result = fileInfoService.findListByCursor(userDto.getUserId(), cursor, pageSize);

        List<FileInfoVO> voList = CopyTools.copyList(result.getList(), FileInfoVO.class);
        CursorPage<FileInfoVO> voResult = CursorPage.of(voList, result.getNextCursor(), result.getPageSize());

        return getSuccessResponseVO(voResult);
    }

    /**
     * 濞撳憡鐖ｉ崚鍡涖€夐弻銉嚄閿涘牊鏁幐浣烘窗瑜版洖鎷伴崚鍡欒鏉╁洦鎶ら敍?
     * 閹恒劏宕橀悽銊ょ艾閺傚洣娆㈤崚妤勩€冮崝鐘烘祰閿涘本鈧嗗厴娴兼ü绨导鐘电埠 OFFSET 閸掑棝銆?
     *
     * @param session  HTTP 娴兼俺鐦?
     * @param filePid  閻栧墎娲拌ぐ鏃綝閿涘牆褰查柅澶涚礆
     * @param category 閺傚洣娆㈤崚鍡欒閿涘牆褰查柅澶涚礆
     * @param cursor   濞撳憡鐖?
     * @param pageSize 濮ｅ繘銆夋径褍鐨?
     * @return 閸掑棝銆夌紒鎾寸亯
     */
    @RequestMapping("/loadDataListCursorV2")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Load Data List with Cursor V2", description = "Cursor-based pagination with filters")
    public ResponseVO<CursorPage<FileInfoVO>> loadDataListCursorV2(
            HttpSession session,
            String filePid,
            String category,
            String cursor,
            Integer pageSize) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);

        FileCategoryEnums categoryEnum = FileCategoryEnums.getByCode(category);
        Integer categoryValue = categoryEnum != null ? categoryEnum.getCategory() : null;

        CursorPage<FileInfo> result = fileInfoService.findListByCursorWithFilter(
                userDto.getUserId(),
                filePid,
                FileDelFlagEnums.USING.getFlag(),
                categoryValue,
                cursor,
                pageSize);

        List<FileInfoVO> voList = CopyTools.copyList(result.getList(), FileInfoVO.class);
        CursorPage<FileInfoVO> voResult = CursorPage.of(voList, result.getNextCursor(), result.getPageSize());

        return getSuccessResponseVO(voResult);
    }

    /**
     * 娑撳﹣绱堕弬鍥︽.
     *
     * @param session    HTTP 娴兼俺鐦?
     * @param fileId     閺傚洣娆D
     * @param file       閺傚洣娆?
     * @param fileName   閺傚洣娆㈤崥?
     * @param filePid    閻栧墎娲拌ぐ鏃綝
     * @param fileMd5    閺傚洣娆D5
     * @param chunkIndex 閸掑棛澧栫槐銏犵穿
     * @param chunks     閹鍨庨悧鍥ㄦ殶
     * @return 娑撳﹣绱剁紒鎾寸亯
     */
    @RequestMapping("/uploadFile")
    @GlobalInterceptor(checkParams = true)
    @com.easypan.annotation.RateLimit(key = "upload", time = 60, count = 30)
    @Operation(summary = "Upload File", description = "Upload file with chunk support")
    public ResponseVO<UploadResultDto> uploadFile(HttpSession session,
            String fileId,
            @org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file,
            @VerifyParam(required = true) String fileName,
            @VerifyParam(required = true) String filePid,
            @VerifyParam(required = true) String fileMd5,
            @VerifyParam(required = true) Integer chunkIndex,
            @VerifyParam(required = true) Integer chunks) {

        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        if (!StringTools.isEmpty(fileId)) {
            UploadPathValidator.validateFileId(fileId);
        }
        UploadResultDto resultDto = fileInfoService.uploadFile(webUserDto, fileId, file, fileName, filePid, fileMd5,
                chunkIndex, chunks);
        return getSuccessResponseVO(resultDto);
    }

    /**
     * 閼惧嘲褰囧韫瑐娴肩姴鍨庨悧鍥︿繆閹垽绱欓悽銊ょ艾閺傤厾鍋ｇ紒顓濈炊閿?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileId  閺傚洣娆D
     * @param filePid 閻栧墎娲拌ぐ鏃綝
     * @return 瀹歌弓绗傛导鐘插瀻閻楀洤鍨悰?
     */
    @RequestMapping("/uploadedChunks")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Get Uploaded Chunks", description = "Get already uploaded chunk indices for resumable upload")
    public ResponseVO<List<Integer>> getUploadedChunks(HttpSession session,
            @VerifyParam(required = true) String fileId,
            @VerifyParam(required = true) String filePid) {

        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        String userId = webUserDto.getUserId();

        File folder = UploadPathValidator.resolveTempFolder(appConfig.getProjectFolder(), userId, fileId).toFile();
        if (!folder.exists() || !folder.isDirectory()) {
            return getSuccessResponseVO(Collections.emptyList());
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return getSuccessResponseVO(Collections.emptyList());
        }

        List<Integer> uploadedChunks = Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(name -> name.chars().allMatch(Character::isDigit))
                .map(Integer::valueOf)
                .sorted()
                .toList();

        return getSuccessResponseVO(uploadedChunks);
    }

    @Resource
    private com.easypan.service.UploadProgressService uploadProgressService;

    /**
     * 閼惧嘲褰囨稉濠佺炊鏉╂稑瀹?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileId  閺傚洣娆D
     * @return 鏉╂稑瀹虫穱鈩冧紖
     */
    @RequestMapping("/getUploadProgress")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Get Upload Progress", description = "Get current upload progress")
    public ResponseVO<com.easypan.entity.dto.UploadProgressDto> getUploadProgress(HttpSession session,
            @VerifyParam(required = true) String fileId) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        com.easypan.entity.dto.UploadProgressDto progress = uploadProgressService.getProgress(webUserDto.getUserId(),
                fileId);
        return getSuccessResponseVO(progress);
    }

    /**
     * 閺屻儴顕楅弬鍥︽鏉烆剛鐖滈悩鑸碘偓?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileId  閺傚洣娆D
     * @return 閸栧懎鎯?status 鐎涙顔岄惃鍕惙鎼?
     */
    @RequestMapping("/transferStatus")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO<com.easypan.entity.dto.FileTransferStatusDto> getTransferStatus(
            HttpSession session,
            @VerifyParam(required = true) String fileId) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, webUserDto.getUserId());
        if (fileInfo == null) {
            throw new BusinessException("File not found");
        }
        com.easypan.entity.dto.FileTransferStatusDto dto = new com.easypan.entity.dto.FileTransferStatusDto();
        dto.setFileId(fileId);
        dto.setStatus(fileInfo.getStatus());
        return getSuccessResponseVO(dto);
    }

    /**
     * SSE: 閼惧嘲褰囨潪顒傜垳閻樿埖鈧焦绁?
     */
    @RequestMapping(value = "/transferStatusSse", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @GlobalInterceptor(checkParams = true)
    public SseEmitter getTransferStatusSse(
            HttpSession session,
            @VerifyParam(required = true) String fileId) {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        AtomicBoolean running = new AtomicBoolean(true);
        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> running.set(false));
        emitter.onError(ex -> running.set(false));

        final SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        virtualThreadExecutor.execute(() -> {
            try {
                int pollCount = 0;
                final int maxPollCount = 900; // 30min / 2s
                while (running.get() && pollCount < maxPollCount) {
                    FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, webUserDto.getUserId());
                    if (fileInfo == null) {
                        emitter.send(SseEmitter.event()
                                .data("{\"status\": 1}"));
                        break;
                    }
                    Integer status = fileInfo.getStatus();
                    emitter.send(SseEmitter.event()
                            .data("{\"status\": " + status + "}"));

                    if (status == 2 || status == 1) {
                        break;
                    }
                    pollCount++;
                    TimeUnit.SECONDS.sleep(2);
                }
                if (running.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                if (running.get()) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    /**
     * 閼惧嘲褰囬崡鏇氶嚋閺傚洣娆㈡穱鈩冧紖.
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileId  閺傚洣娆D
     * @return 閺傚洣娆㈡穱鈩冧紖
     */
    @RequestMapping("/getFileInfo/{fileId}")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Get File Info", description = "Get single file info by fileId")
    public ResponseVO<FileInfoVO> getFileInfo(
            HttpSession session,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, webUserDto.getUserId());
        if (fileInfo == null) {
            throw new BusinessException("File not found");
        }
        return getSuccessResponseVO(CopyTools.copy(fileInfo, FileInfoVO.class));
    }

    /**
     * 閼惧嘲褰囬崶鍓у.
     *
     * @param session     HTTP 娴兼俺鐦?
     * @param response    HTTP 閸濆秴绨?
     * @param imageFolder 閸ュ墽澧栭弬鍥︽婢?
     * @param imageName   閸ュ墽澧栭崥宥囆?
     */
    @RequestMapping("/getImage/{imageFolder}/{imageName}")
    @GlobalInterceptor(checkLogin = true)
    @Operation(summary = "Get Image", description = "Get image by folder and name")
    public void getImage(HttpSession session, HttpServletResponse response,
            @PathVariable("imageFolder") String imageFolder,
            @PathVariable("imageName") String imageName) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        super.getImage(response, imageFolder, imageName, webUserDto.getUserId());
    }

    /**
     * 閺嶈宓佺憴鍡涱暥id閼惧嘲褰囩憴鍡涱暥閸掑棛澧?
     *
     * @param response HTTP 閸濆秴绨?
     * @param session  HTTP 娴兼俺鐦?
     * @param fileId   閺傚洣娆D
     */
    @RequestMapping("/ts/getVideoInfo/{fileId}")
    @GlobalInterceptor
    @Operation(summary = "Get Video Info", description = "Get video m3u8 or ts file")
    public void getVideoInfo(HttpServletResponse response, HttpSession session,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        super.getFile(response, fileId, webUserDto.getUserId());
    }

    /**
     * 閺嶈宓侀弬鍥︽id閼惧嘲褰囬弬鍥︽.
     *
     * @param response HTTP 閸濆秴绨?
     * @param session  HTTP 娴兼俺鐦?
     * @param fileId   閺傚洣娆D
     */
    @RequestMapping("/getFile/{fileId}")
    @GlobalInterceptor
    @Operation(summary = "Get File", description = "Get file content")
    public void getFile(HttpServletResponse response, HttpSession session,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        super.getFile(response, fileId, webUserDto.getUserId());
    }

    /**
     * 閺傛澘缂撻弬鍥︽婢?
     *
     * @param session  HTTP 娴兼俺鐦?
     * @param filePid  閻栧墎娲拌ぐ鏃綝
     * @param fileName 閺傚洣娆㈤崥?
     * @return 閺傚洣娆㈡穱鈩冧紖
     */
    @RequestMapping("/newFoloder")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "New Folder", description = "Create a new folder")
    public ResponseVO<FileInfo> newFoloder(HttpSession session,
            @VerifyParam(required = true) String filePid,
            @VerifyParam(required = true) String fileName) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.newFolder(filePid, webUserDto.getUserId(), fileName);
        return getSuccessResponseVO(fileInfo);
    }

    /**
     * 閼惧嘲褰囬弬鍥︽婢堕€涗繆閹?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param path    鐠侯垰绶?
     * @return 閺傚洣娆㈡径鐟板灙鐞?
     */
    @RequestMapping("/getFolderInfo")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Get Folder Info", description = "Get folder path info")
    public ResponseVO<List<FolderVO>> getFolderInfo(HttpSession session,
            @VerifyParam(required = true) @Parameter(description = "Folder Path") String path) {
        return super.getFolderInfo(path, getUserInfoFromSession(session).getUserId());
    }

    /**
     * 闁插秴鎳￠崥?
     *
     * @param session  HTTP 娴兼俺鐦?
     * @param fileId   閺傚洣娆D
     * @param fileName 閺傜増鏋冩禒璺烘倳
     * @return 閺傚洣娆㈡穱鈩冧紖
     */
    @RequestMapping("/rename")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Rename File", description = "Rename a file or folder")
    public ResponseVO<FileInfoVO> rename(HttpSession session,
            @VerifyParam(required = true) String fileId,
            @VerifyParam(required = true) String fileName) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.rename(fileId, webUserDto.getUserId(), fileName);
        return getSuccessResponseVO(CopyTools.copy(fileInfo, FileInfoVO.class));
    }

    /**
     * 閸旂姾娴囬幍鈧張澶嬫瀮娴?
     *
     * @param session        HTTP 娴兼俺鐦?
     * @param filePid        閻栧墎娲拌ぐ鏃綝
     * @param currentFileIds 瑜版挸澧犻弬鍥︽ID閸掓銆?
     * @return 閺傚洣娆㈤崚妤勩€?
     */
    @RequestMapping("/loadAllFolder")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Load All Folders", description = "Load all folders for moving files")
    public ResponseVO<List<FileInfoVO>> loadAllFolder(HttpSession session,
            @VerifyParam(required = true) String filePid,
            String currentFileIds) {
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(getUserInfoFromSession(session).getUserId());
        query.setFilePid(filePid);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        if (!StringTools.isEmpty(currentFileIds)) {
            query.setExcludeFileIdArray(currentFileIds.split(","));
        }
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setOrderBy("create_time desc");
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(query);
        return getSuccessResponseVO(CopyTools.copyList(fileInfoList, FileInfoVO.class));
    }

    /**
     * 閺囧瓨鏁奸弬鍥︽閻╊喖缍?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileIds 閺傚洣娆D閸掓銆?
     * @param filePid 閻╊喗鐖ｉ惄顔肩秿ID
     * @return 閸濆秴绨茬€电钖?
     */
    @RequestMapping("/changeFileFolder")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Change File Folder", description = "Move files to another folder")
    public ResponseVO<Void> changeFileFolder(HttpSession session,
            @VerifyParam(required = true) String fileIds,
            @VerifyParam(required = true) String filePid) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        fileInfoService.changeFileFolder(fileIds, filePid, webUserDto.getUserId());
        return getSuccessResponseVO(null);
    }

    /**
     * 閸掓稑缂撴稉瀣祰闁剧偓甯?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileId  閺傚洣娆D
     * @return 娑撳娴囬惍?
     */
    @RequestMapping("/createDownloadUrl/{fileId}")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Create Download URL", description = "Create a temporary download URL")
    public ResponseVO<String> createDownloadUrl(HttpSession session,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        return super.createDownloadUrl(fileId, getUserInfoFromSession(session).getUserId());
    }

    /**
     * 娑撳娴?
     *
     * @param request  HTTP 鐠囬攱鐪?
     * @param response HTTP 閸濆秴绨?
     * @param code     娑撳娴囬惍?
     * @throws Exception 瀵倸鐖?
     */
    @RequestMapping("/download/{code}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    @Operation(summary = "Download File", description = "Download file by code")
    public void download(HttpServletRequest request, HttpServletResponse response,
            @PathVariable("code") @VerifyParam(required = true) String code) throws Exception {
        super.download(request, response, code);
    }

    /**
     * 閸掔娀娅庨弬鍥︽.
     *
     * @param session HTTP 娴兼俺鐦?
     * @param fileIds 閺傚洣娆D閸掓銆?
     * @return 閸濆秴绨茬€电钖?
     */
    @RequestMapping("/delFile")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Delete File", description = "Delete files to recycle bin")
    public ResponseVO<Void> delFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        fileInfoService.removeFile2RecycleBatch(webUserDto.getUserId(), fileIds);
        return getSuccessResponseVO(null);
    }

    /**
     * 閹靛綊鍣烘稉瀣祰.
     *
     * @param response HTTP 閸濆秴绨?
     * @param session  HTTP 娴兼俺鐦?
     * @param fileIds  閺傚洣娆D閸掓銆?
     */
    @RequestMapping("/batchDownload/{fileIds}")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Batch Download", description = "Download multiple files as zip")
    public void batchDownload(HttpServletResponse response, HttpSession session,
            @PathVariable("fileIds") @VerifyParam(required = true) String fileIds) {
        try {
            SessionWebUserDto webUserDto = getUserInfoFromSession(session);
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"download.zip\"");
            fileOperationService.downloadMultipleFiles(
                    webUserDto.getUserId(),
                    Arrays.asList(fileIds.split(",")),
                    response.getOutputStream());
        } catch (IOException e) {
            throw new BusinessException("Download failed, please retry");
        }
    }
}
