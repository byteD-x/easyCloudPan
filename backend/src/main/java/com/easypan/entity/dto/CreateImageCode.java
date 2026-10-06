package com.easypan.entity.dto;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * 验证码图片生成器.
 */
public class CreateImageCode {
    private int width = 160;
    private int height = 40;
    private int codeCount = 4;
    private int lineCount = 20;
    private String code = null;
    private BufferedImage buffImg = null;
    Random random = new Random();

    /**
     * 默认构造函数.
     */
    public CreateImageCode() {
        creatImage();
    }

    /**
     * 指定宽度和高度的构造函数.
     *
     * @param width 图片宽度
     * @param height 图片高度
     */
    public CreateImageCode(int width, int height) {
        this.width = width;
        this.height = height;
        creatImage();
    }

    /**
     * 指定宽度、高度和验证码字符数的构造函数.
     *
     * @param width 图片宽度
     * @param height 图片高度
     * @param codeCount 验证码字符数
     */
    public CreateImageCode(int width, int height, int codeCount) {
        this.width = width;
        this.height = height;
        this.codeCount = codeCount;
        creatImage();
    }

    /**
     * 指定宽度、高度、验证码字符数和干扰线数的构造函数.
     *
     * @param width 图片宽度
     * @param height 图片高度
     * @param codeCount 验证码字符数
     * @param lineCount 干扰线数
     */
    public CreateImageCode(int width, int height, int codeCount, int lineCount) {
        this.width = width;
        this.height = height;
        this.codeCount = codeCount;
        this.lineCount = lineCount;
        creatImage();
    }

    private void creatImage() {
        int fontWidth = width / codeCount;
        int codeY = height - 8;

        buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = buffImg.getGraphics();
        g.setColor(getRandColor(200, 250));
        g.fillRect(0, 0, width, height);
        int fontHeight = height - 5;
        Font font = new Font("Fixedsys", Font.BOLD, fontHeight);
        g.setFont(font);

        for (int i = 0; i < lineCount; i++) {
            int xs = random.nextInt(width);
            int ys = random.nextInt(height);
            int xe = xs + random.nextInt(width);
            int ye = ys + random.nextInt(height);
            g.setColor(getRandColor(1, 255));
            g.drawLine(xs, ys, xe, ye);
        }

        float yawpRate = 0.01f;
        int area = (int) (yawpRate * width * height);
        for (int i = 0; i < area; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            buffImg.setRGB(x, y, random.nextInt(255));
        }

        String str1 = randomStr(codeCount);
        this.code = str1;
        for (int i = 0; i < codeCount; i++) {
            String strRand = str1.substring(i, i + 1);
            g.setColor(getRandColor(1, 255));
            g.drawString(strRand, i * fontWidth + 3, codeY);
        }
    }

    private String randomStr(int n) {
        String str1 = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz123456789";
        StringBuilder sb = new StringBuilder(n);
        int len = str1.length() - 1;
        double r;
        for (int i = 0; i < n; i++) {
            r = (Math.random()) * len;
            sb.append(str1.charAt((int) r));
        }
        return sb.toString();
    }

    private Color getRandColor(int fc, int bc) {
        if (fc > 255) {
            fc = 255;
        }
        if (bc > 255) {
            bc = 255;
        }
        int r = fc + random.nextInt(bc - fc);
        int g = fc + random.nextInt(bc - fc);
        int b = fc + random.nextInt(bc - fc);
        return new Color(r, g, b);
    }

    /**
     * 将验证码图片写入输出流.
     *
     * @param sos 输出流
     * @throws IOException IO 异常
     */
    public void write(OutputStream sos) throws IOException {
        ImageIO.write(buffImg, "png", sos);
        sos.close();
    }

    public BufferedImage getBuffImg() {
        return buffImg;
    }

    public String getCode() {
        return code.toLowerCase();
    }
}