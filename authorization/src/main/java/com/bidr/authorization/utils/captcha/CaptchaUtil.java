package com.bidr.authorization.utils.captcha;

import org.springframework.core.io.DefaultResourceLoader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * @author Sharp
 */
public class CaptchaUtil {

    private static final String RANDOM_STR = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int FONT_SIZE = 30;
    private static Font hanSansFont = null;
    private static Random random = new Random();
    private static final int width = 80;
    private static final int height = 50;
    private static final int lineNum = 50;
    private static final int strNum = 4;

    /**
     * 生成随机图片
     */
    public static BufferedImage genRandomCodeImage(StringBuffer randomCode) {
        // BufferedImage类是具有缓冲区的Image类
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        // 获取Graphics对象,便于对图像进行各种绘制操作
        Graphics g = image.getGraphics();
        // 设置背景色
        g.setColor(getRandColor(200, 250));
        g.fillRect(0, 0, width, height);

        // 设置干扰线的颜色
        g.setColor(getRandColor(110, 120));

        // 绘制干扰线
        for (int i = 0; i <= lineNum; i++) {
            drawLine(g);
        }
        // 绘制随机字符
        g.setFont(getHanSansFont(Font.PLAIN, FONT_SIZE));
        for (int i = 1; i <= strNum; i++) {
            randomCode.append(drawString(g, i));
        }
        g.dispose();
        return image;
    }

    /**
     * 给定范围获得随机颜色
     */
    private static Color getRandColor(int fc, int bc) {
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
     * 绘制干扰线
     */
    private static void drawLine(Graphics g) {
        int x = random.nextInt(width);
        int y = random.nextInt(height);
        int x0 = random.nextInt(16);
        int y0 = random.nextInt(16);
        g.drawLine(x, y, x + x0, y + y0);
    }

    private static Font getHanSansFont(int fontStyle, float fontSize) {
        if (hanSansFont == null) {
            try {
                DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
                InputStream inputStream =
                        resourceLoader.getResource("classpath:/SourceHanSansCN-Regular.otf").getInputStream();
                hanSansFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            } catch (FontFormatException | IOException e) {
                e.printStackTrace();
            }
        }
        return hanSansFont.deriveFont(fontSize);
    }

    /**
     * 绘制字符串
     */
    private static String drawString(Graphics g, int i) {
        g.setColor(new Color(random.nextInt(101), random.nextInt(111), random.nextInt(121)));
        String rand = getRandomString(random.nextInt(RANDOM_STR.length()));
        g.translate(random.nextInt(3), random.nextInt(3));
        g.drawString(rand, 13 * i, 40);
        return rand;
    }

    /**
     * 获取随机的字符
     */
    private static String getRandomString(int num) {
        return String.valueOf(RANDOM_STR.charAt(num));
    }
}
