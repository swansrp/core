package com.bidr.platform.utils;

import com.bidr.kernel.utils.FuncUtil;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author sharp
 */
@Slf4j
public class QrCodeUtil {
    /**
     * 定义二维码的基本尺寸和颜色
     * 二维码宽度，单位像素。
     */
    private static final int CODE_WIDTH = 500;
    /**
     * 二维码高度，单位像素。
     */
    private static final int CODE_HEIGHT = 500;
    /**
     * 二维码前景色，0x000000 表示黑色。
     */
    private static final int FRONT_COLOR = 0x000000;
    /**
     * 二维码背景色，0xFFFFFF 表示白色。
     */
    private static final int BACKGROUND_COLOR = 0xFFFFFF;

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        // 创建一个新的 BufferedImage 对象，用于存储调整大小后的图像，设置宽度、高度和类型（TYPE_INT_RGB，表示 24 位 RGB 颜色）。

        Graphics2D g = resizedImage.createGraphics();
        // 在新的 BufferedImage 对象上创建一个 Graphics2D 对象，用于绘制调整后的图像。

        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        // 在新的 BufferedImage 对象上绘制原始图像，指定目标宽度和高度，使其调整大小。

        g.dispose();
        // 释放 Graphics2D 对象占用的资源。

        return resizedImage;
        // 返回调整大小后的 BufferedImage 对象。
    }

    /**
     * 创建二维码并保存到文件。
     *
     * @param codeContent        二维码内容
     * @param codeImgFileSaveDir 二维码图片保存的目录
     * @param fileName           二维码图片文件名称
     */
    public static void generateQrCode(String codeContent, File codeImgFileSaveDir, String fileName, String logoPath) {
        try {
            if (FuncUtil.isEmpty(codeContent)) {
                log.info("二维码内容为空，不进行操作...");
                return;
            }
            codeContent = codeContent.trim();
            // 如果二维码内容为空，记录日志并返回，不进行后续操作。否则，去除二维码内容两端的空白字符。

            if (codeImgFileSaveDir == null || codeImgFileSaveDir.isFile()) {
                codeImgFileSaveDir = FileSystemView.getFileSystemView().getHomeDirectory();
                log.info("二维码图片存在目录为空，默认放在桌面...");
            }
            // 如果二维码图片保存目录为空或为文件（而不是目录），将保存目录设置为桌面目录，并记录日志。

            if (!codeImgFileSaveDir.exists()) {
                codeImgFileSaveDir.mkdirs();
                log.info("二维码图片存在目录不存在，开始创建...");
            }
            // 如果保存目录不存在，创建该目录，并记录日志。

            if (FuncUtil.isEmpty(fileName)) {
                fileName = System.currentTimeMillis() + ".png";
                log.info("二维码图片文件名为空，随机生成 png 格式图片...");
            }
            // 如果二维码图片文件名为空，使用当前时间戳作为文件名，并设置为 png 格式，记录日志。

            // 使用新方法生成带有 logo 的二维码图像
            BufferedImage bufferedImage = getBufferedImageWithLogo(codeContent, logoPath);

            File codeImgFile = new File(codeImgFileSaveDir, fileName);
            // 创建一个 File 对象，表示二维码图片文件，使用保存目录和文件名。

            ImageIO.write(bufferedImage, "png", codeImgFile);
            // 使用 ImageIO 将带有 logo 的二维码图像写入文件，指定文件格式为 png。

            log.info("二维码图片生成成功：{}", codeImgFile.getPath());
            // 记录日志，打印二维码图片生成成功的消息和文件路径。
        } catch (Exception e) {
            log.error("", e);
            // 如果在生成二维码图片过程中发生异常，打印异常堆栈信息。
        }
    }

    public static void generateQrCode(String codeContent, OutputStream outputStream, byte[] logoBytes) {
        try {
            if (FuncUtil.isEmpty(codeContent)) {
                log.info("二维码内容为空，不进行操作...");
                return;
            }
            codeContent = codeContent.trim();
            // 如果二维码内容为空，记录日志并返回，不进行后续操作。否则，去除二维码内容两端的空白字符。
            BufferedImage bufferedImage = getBufferedImageWithLogo(codeContent, logoBytes);
            // 写入响应流
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ImageIO.write(bufferedImage, "png", bos);
                byte[] imageBytes = bos.toByteArray();
                outputStream.write(imageBytes);
            } catch (Exception e) {
                log.error("发生错误", e);
            }
            // 将包含 Base64 编码的二维码图像数据写入输出流，格式为"data:image/png;base64,"加上编码后的字符串。
            log.info("二维码图片生成到输出流成功...");
            // 记录日志，打印二维码图片生成到输出流成功的消息。
        } catch (Exception e) {
            log.error("发生错误", e);
            // 如果在生成二维码图片过程中发生其他异常，打印异常堆栈信息，并记录错误日志，打印错误消息。
        }
    }

    public static BufferedImage getBufferedImageWithLogo(String codeContent, byte[] logoBytes) {
        log.info("生成带有 logo 的二维码，内容：{}，logo 字节数组长度：{}", codeContent, logoBytes != null ? logoBytes.length : 0);
        // 记录日志，表明正在生成带有 logo 的二维码，并打印二维码内容和 logo 字节数组的长度（如果不为 null）。
        try {
            BufferedImage bufferedImage = getBufferedImage(codeContent);
            // 添加 logo
            if (FuncUtil.isNotEmpty(logoBytes)) {
                log.info("添加 logo，字节数组长度：{}", logoBytes.length);
                // 如果 logo 字节数组不为 null 且长度大于 0，记录日志并打印 logo 字节数组的长度。
                BufferedImage logoImage = ImageIO.read(new ByteArrayInputStream(logoBytes));
                // 使用 ImageIO 从字节数组输入流中读取 logo 图像，创建一个 BufferedImage 对象表示 logo 图像。
                int logoWidth = bufferedImage.getWidth() / 4;
                int logoHeight = bufferedImage.getHeight() / 4;
                // 计算 logo 图像在二维码中的大小，这里将 logo 的宽度和高度设置为二维码宽度和高度的四分之一。
                BufferedImage resizedLogoImage = resizeImage(logoImage, logoWidth, logoHeight);
                // 调用 resizeImage 方法，将 logo 图像调整为计算出的大小，得到调整后的 BufferedImage 对象。
                addLogo(bufferedImage, logoWidth, logoHeight, resizedLogoImage);
            }

            return bufferedImage;
            // 返回带有 logo 的二维码图像。
        } catch (IOException e) {
            log.error("生成带有 logo 的二维码时发生错误", e);
            // 如果在生成二维码过程中发生异常，打印异常堆栈信息，并记录错误日志，打印错误消息。
            // 可以根据需要返回一个默认的图像或者抛出一个自定义异常
            return null;
            // 在发生错误时，返回 null，表示生成二维码失败。
        }
    }

    public static BufferedImage getBufferedImage(String codeContent) {
        try {

            // 记录日志，表明正在生成带有 logo 的二维码，并打印二维码内容和 logo 路径。

            // 设置编码提示类型
            Map<EncodeHintType, Object> hints = new HashMap<>();
            // 创建一个 HashMap 来存储编码提示类型和对应的值。

            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            // 设置编码字符集为 UTF-8，确保二维码中的文本可以正确编码和解码。

            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            // 设置二维码的错误纠正级别为 M（中等），提高二维码的容错能力。

            hints.put(EncodeHintType.MARGIN, 1);
            // 设置二维码的边距为 1，控制二维码周围的空白区域大小。

            // 创建多格式写入器实例
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            // 创建一个 MultiFormatWriter 对象，用于生成不同格式的条形码和二维码。

            // 生成二维码的 BitMatrix
            BitMatrix bitMatrix = multiFormatWriter.encode(codeContent, BarcodeFormat.QR_CODE, CODE_WIDTH, CODE_HEIGHT, hints);
            // 使用 MultiFormatWriter 的 encode 方法生成二维码的位矩阵，传入二维码内容、格式（QR_CODE）、宽度、高度和编码提示类型。

            MatrixToImageConfig matrixToImageConfig = new MatrixToImageConfig(0xFF000001, 0xFFFFFFFF);
            return MatrixToImageWriter.toBufferedImage(bitMatrix, matrixToImageConfig);
            // 遍历二维码的位矩阵，根据位矩阵中的值设置图像的像素颜色，将前景色（黑色）或背景色（白色）设置到 BufferedImage 中。
        } catch (WriterException e) {
            log.error("生成带有 logo 的二维码时发生错误", e);
            // 如果在生成二维码过程中发生异常，打印异常堆栈信息，并记录错误日志，打印错误消息。

            // 可以根据需要返回一个默认的图像或者抛出一个自定义异常
            return null;
            // 在发生错误时，返回 null，表示生成二维码失败。
        }


    }

    /**
     * @param codeContent 二维码的内容
     * @param logoPath    logo的路径
     * @return
     */
    public static BufferedImage getBufferedImageWithLogo(String codeContent, String logoPath) {
        log.info("生成带有 logo 的二维码，内容：{}，logo 路径：{}", codeContent, logoPath);
        try {
            BufferedImage bufferedImage = getBufferedImage(codeContent);
            // 添加 logo
            if (FuncUtil.isNotEmpty(logoPath)) {
                log.info("添加 logo，路径：{}", logoPath);
                // 如果 logo 路径不为空且不为空字符串，记录日志并打印 logo 路径。
                BufferedImage logoImage = ImageIO.read(new File(logoPath));
                // 使用 ImageIO 读取 logo 图像文件，创建一个 BufferedImage 对象表示 logo 图像。
                int logoWidth = bufferedImage.getWidth() / 5;
                int logoHeight = bufferedImage.getHeight() / 5;
                // 计算 logo 图像在二维码中的大小，这里将 logo 的宽度和高度设置为二维码宽度和高度的五分之一。
                BufferedImage resizedLogoImage = resizeImage(logoImage, logoWidth, logoHeight);
                // 调用 resizeImage 方法，将 logo 图像调整为计算出的大小，得到调整后的 BufferedImage 对象。

                addLogo(bufferedImage, logoWidth, logoHeight, resizedLogoImage);
            }

            return bufferedImage;
            // 返回带有 logo 的二维码图像。
        } catch (IOException e) {
            log.error("生成带有 logo 的二维码时发生错误", e);
            // 如果在生成二维码过程中发生异常，打印异常堆栈信息，并记录错误日志，打印错误消息。

            // 可以根据需要返回一个默认的图像或者抛出一个自定义异常
            return null;
            // 在发生错误时，返回 null，表示生成二维码失败。
        }
    }

    private static void addLogo(BufferedImage bufferedImage, int logoWidth, int logoHeight, BufferedImage resizedLogoImage) {
        Graphics2D g = bufferedImage.createGraphics();
        // 在二维码图像上创建一个 Graphics2D 对象，用于绘制 logo。

        int x = (bufferedImage.getWidth() - logoWidth) / 2;
        int y = (bufferedImage.getHeight() - logoHeight) / 2;
        // 计算 logo 在二维码图像中的位置，使其居中显示。

        g.drawImage(resizedLogoImage, x, y, null);
        // 在二维码图像上绘制调整后的 logo 图像，指定位置为计算出的坐标。

        g.dispose();
        // 释放 Graphics2D 对象占用的资源。
    }

    /**
     * 解析本地二维码图片内容。
     *
     * @param file 本地二维码图片文件
     * @return 二维码内容
     * @throws Exception 如果解析失败
     */
    public static String parseQrCode(File file) {
        String resultStr = null;
        // 初始化结果字符串为 null，表示如果解析失败，将返回 null。
        // 检查文件是否有效
        if (file == null || file.isDirectory() || !file.exists()) {
            return resultStr;
        }
        // 如果文件为空、是目录或不存在，直接返回 null，表示无法解析。
        try {
            // 读取本地图片文件
            BufferedImage bufferedImage = ImageIO.read(file);
            // 使用 ImageIO 读取本地二维码图片文件，创建一个 BufferedImage 对象表示图片。
            resultStr = parseQrCode(bufferedImage);
        } catch (IOException e) {
            log.error("打开图片文件失败, 路径是: {}!", file.getPath(), e);
        } catch (NotFoundException e) {
            log.error("图片非二维码图片, 路径是: {}!", file.getPath(), e);
            // 如果找不到二维码或者图片不是二维码，打印错误堆栈信息，并记录错误日志，打印图片路径。
        }
        return resultStr;
        // 返回解析得到的二维码内容，如果解析失败，返回 null。
    }

    private static String parseQrCode(BufferedImage bufferedImage) throws NotFoundException {
        // 将 BufferedImage 转换为 LuminanceSource
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        // 创建一个 BufferedImageLuminanceSource 对象，将读取的 BufferedImage 转换为亮度源，用于二维码解析。

        // 创建二进制位图
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        // 创建一个 BinaryBitmap 对象，使用 HybridBinarizer 对亮度源进行二值化处理，得到二进制位图，用于二维码解析。

        // 设置解码提示类型
        Hashtable<DecodeHintType, String> hints = new Hashtable<>();
        // 创建一个 Hashtable 来存储解码提示类型和对应的值。

        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        // 设置解码字符集为 UTF-8，确保二维码中的文本可以正确解码。

        // 解码二维码
        Result result = new MultiFormatReader().decode(bitmap, hints);
        // 使用 MultiFormatReader 对二进制位图进行解码，传入解码提示类型，得到 Result 对象表示解码结果。

        // 从解码结果中获取二维码的文本内容，并赋值给 resultStr。
        return result.getText();
    }

    /**
     * 解析网络二维码图片内容。
     *
     * @param url 二维码图片网络地址
     * @return 二维码内容
     * @throws Exception 如果解析失败
     */
    public static String parseQrCode(URL url) {
        String resultStr = null;
        // 初始化结果字符串为 null，表示如果解析失败，将返回 null。

        if (FuncUtil.isEmpty(url)) {
            return resultStr;
        }
        // 如果 URL 为空，直接返回 null，表示无法解析。

        try {
            // 读取网络图片文件
            BufferedImage bufferedImage = ImageIO.read(url);
            // 使用 ImageIO 读取网络二维码图片文件，创建一个 BufferedImage 对象表示图片。

            // 将 BufferedImage 转换为 LuminanceSource
            resultStr = parseQrCode(bufferedImage);
            // 从解码结果中获取二维码的文本内容，并赋值给 resultStr。
        } catch (IOException e) {
            log.error("二维码图片地址错误, 地址是: {}", url, e);
            // 如果读取网络图片时发生 IOException，打印错误堆栈信息，并记录错误日志，打印图片地址。
        } catch (NotFoundException e) {
            log.error("图片非二维码图片, 地址是: {}", url, e);
            // 如果找不到二维码或者图片不是二维码，打印错误堆栈信息，并记录错误日志，打印图片地址。
        }
        return resultStr;
        // 返回解析得到的二维码内容，如果解析失败，返回 null。
    }


}
