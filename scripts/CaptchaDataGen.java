import com.easypan.entity.dto.CreateImageCode;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CaptchaDataGen {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: CaptchaDataGen <outputDir> <count>");
            return;
        }
        String outputDir = args[0];
        int count = Integer.parseInt(args[1]);

        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir);
        }

        File labels = new File(dir, "labels.csv");
        try (FileWriter fw = new FileWriter(labels, false)) {
            fw.write("file,code\n");
            for (int i = 0; i < count; i++) {
                CreateImageCode cic = new CreateImageCode(130, 38, 5, 10);
                String code = cic.getCode();
                String name = String.format("cap_%05d.png", i);
                File out = new File(dir, name);
                ImageIO.write(cic.getBuffImg(), "png", out);
                fw.write(name + "," + code + "\n");
            }
        }

        System.out.println("Generated " + count + " samples at " + dir.getAbsolutePath());
    }
}
