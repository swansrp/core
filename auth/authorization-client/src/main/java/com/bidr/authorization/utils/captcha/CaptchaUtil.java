package com.bidr.authorization.utils.captcha;

import org.springframework.core.io.DefaultResourceLoader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * 验证码生成工具类
 * 优化版本：更清晰、更美观、更易识别
 *
 * @author Sharp
 */
public class CaptchaUtil {

    /**
     * 验证码字符集（排除容易混淆的字符：0/O, 1/I/L, 2/Z, 5/S, 8/B）
     */
    private static final String RANDOM_STR = "34679ACDEFGHJKMNPQRTUVWXY";

    private static final int FONT_SIZE = 38;
    private static final int width = 140;
    private static final int height = 50;
    /**
     * 干扰线数量（适当减少，避免影响识别）
     */
    private static final int lineNum = 8;
    /**
     * 干扰点数量
     */
    private static final int dotNum = 50;
    private static final int strNum = 4;
    private static final Random random = new Random();
    private static Font hanSansFont = null;

    /**
     * 生成随机图片
     */
    public static BufferedImage genRandomCodeImage(StringBuffer randomCode) {
        // 使用TYPE_INT_RGB获得更好的显示效果
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 开启抗锯齿，使文字更清晰
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 设置渐变背景色，更美观
        GradientPaint gradient = new GradientPaint(0, 0, new Color(240, 240, 240),
                width, height, new Color(220, 230, 240));
        g.setPaint(gradient);
        g.fillRect(0, 0, width, height);

        // 绘制干扰点（比干扰线更柔和）
        for (int i = 0; i < dotNum; i++) {
            drawDot(g);
        }

        // 绘制干扰线
        for (int i = 0; i < lineNum; i++) {
            drawLine(g);
        }

        // 绘制随机字符
        g.setFont(getHanSansFont(Font.BOLD, FONT_SIZE));
        for (int i = 0; i < strNum; i++) {
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
        int r = fc + random.nextInt(bc - fc + 1);
        int g = fc + random.nextInt(bc - fc + 1);
        int b = fc + random.nextInt(bc - fc + 1);
        return new Color(r, g, b);
    }

    /**
     * 绘制干扰点
     */
    private static void drawDot(Graphics2D g) {
        int x = random.nextInt(width);
        int y = random.nextInt(height);
        g.setColor(getRandColor(150, 200));
        g.fillOval(x, y, 2, 2);
    }

    /**
     * 绘制干扰线（贝塞尔曲线，更自然）
     */
    private static void drawLine(Graphics2D g) {
        g.setColor(getRandColor(160, 200));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int x1 = random.nextInt(width / 4);
        int y1 = random.nextInt(height);
        int x2 = random.nextInt(width / 4) + width * 3 / 4;
        int y2 = random.nextInt(height);

        int ctrlx = random.nextInt(width / 2) + width / 4;
        int ctrly = random.nextInt(height);

        // 绘制二次贝塞尔曲线
        g.draw(new java.awt.geom.QuadCurve2D.Float(x1, y1, ctrlx, ctrly, x2, y2));
    }

    private static Font getHanSansFont(int fontStyle, float fontSize) {
        if (hanSansFont == null) {
            try {
                DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
                InputStream inputStream =
                        resourceLoader.getResource("classpath:/SourceHanSansCN-Regular.ttf").getInputStream();
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
    private static String drawString(Graphics2D g, int i) {
        // 使用深色系，确保和背景有足够对比度
        g.setColor(new Color(30 + random.nextInt(80), 30 + random.nextInt(80), 30 + random.nextInt(80)));
        String rand = getRandomString(random.nextInt(RANDOM_STR.length()));

        // 计算字符位置，增加间距
        int x = 15 + i * 30 + random.nextInt(5);
        int y = 35 + random.nextInt(8);

        // 随机旋转角度（-15° 到 15°），增加美观度
        double angle = (random.nextDouble() - 0.5) * 0.5;
        g.rotate(angle, x + FONT_SIZE / 2, y - FONT_SIZE / 2);
        g.drawString(rand, x, y);
        // 旋转回来
        g.rotate(-angle, x + FONT_SIZE / 2, y - FONT_SIZE / 2);

        return rand;
    }

    /**
     * 获取随机的字符
     */
    private static String getRandomString(int num) {
        return String.valueOf(RANDOM_STR.charAt(num));
    }
}
