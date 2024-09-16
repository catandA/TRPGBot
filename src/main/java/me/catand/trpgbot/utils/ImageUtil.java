package me.catand.trpgbot.utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Random;

public class ImageUtil {
	public enum backGround {
		BG,
		BURNING_CHINNABER,
		CANDYCANE,
		DRACONIC,
		LIGHT,
		NIGHTBLUE,
		SKYLEA,
		SUNRISE;

		@Override
		public String toString() {
			return switch (this) {
				case BG -> "/background/bg.png";
				case BURNING_CHINNABER -> "/background/burning-cinnabar.png";
				case CANDYCANE -> "/background/candycane.png";
				case DRACONIC -> "/background/draconic.png";
				case LIGHT -> "/background/light.png";
				case NIGHTBLUE -> "/background/nightblue.png";
				case SKYLEA -> "/background/skylea.png";
				case SUNRISE -> "/background/sunrise.png";
			};
		}
	}

	public static BufferedImage getBufferedImageBackground(int width, int height) throws IOException {
		// 创建一个指定长宽的图片
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		// 获取 Graphics2D 对象
		Graphics2D g2d = image.createGraphics();

		// 加载一个随机的背景图像
		Random random = new Random();
		backGround bg = backGround.values()[random.nextInt(backGround.values().length)];
		InputStream bgImageStream = ImageUtil.class.getResourceAsStream(bg.toString());
		BufferedImage bgImage = ImageIO.read(bgImageStream);

		// 计算背景图像的缩放比例和位置
		float scale = Math.max((float) width / bgImage.getWidth(), (float) height / bgImage.getHeight());
		int scaledWidth = (int) (bgImage.getWidth() * scale);
		int scaledHeight = (int) (bgImage.getHeight() * scale);
		int left = (width - scaledWidth) / 2;
		int top = (height - scaledHeight) / 2;

		// 绘制背景图像
		g2d.drawImage(bgImage, left, top, scaledWidth, scaledHeight, null);

		g2d.dispose();

		return image;
	}

	public static Image getImageBackground(int width, int height) {
		try {
			BufferedImage bufferedImage = getBufferedImageBackground(width, height);
			return convertToFXImage(bufferedImage);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Image scaleImage(Image originalImage, int targetWidth, int targetHeight) {
		double scaleFactor = Math.min(
				targetWidth / originalImage.getWidth(),
				targetHeight / originalImage.getHeight()
		);

		int width = (int) (originalImage.getWidth() * scaleFactor);
		int height = (int) (originalImage.getHeight() * scaleFactor);

		WritableImage writableImage = new WritableImage(width, height);
		PixelReader reader = originalImage.getPixelReader();
		PixelWriter writer = writableImage.getPixelWriter();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				double srcX = x / scaleFactor;
				double srcY = y / scaleFactor;

				javafx.scene.paint.Color color = reader.getColor((int) srcX, (int) srcY);
				writer.setColor(x, y, color);
			}
		}

		return writableImage;
	}

	public static String ImageToBase64(BufferedImage image) throws IOException {
		// 创建一个用于输出的ByteArrayOutputStream
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		// 将BufferedImage编码成JPEG格式，并写入输出流
		ImageIO.write(image, "png", outputStream);

		// 使用Base64编码字节流
		return "base64://" + Base64.getEncoder().encodeToString(outputStream.toByteArray());
	}

	public static BufferedImage addHeader(BufferedImage image, int headerHeight, Color color) {
		// 获取原始 BufferedImage 对象的宽度和高度
		int width = image.getWidth();
		int height = image.getHeight();

		// 创建一个新的 BufferedImage 对象，其高度是原始 BufferedImage 对象的高度加上边的高度
		BufferedImage newImage = new BufferedImage(width, height + headerHeight, BufferedImage.TYPE_INT_ARGB);

		// 获取 Graphics2D 对象
		Graphics2D g2d = newImage.createGraphics();

		// 绘制边
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, headerHeight);

		// 绘制原始 BufferedImage 对象的图像
		g2d.drawImage(image, 0, headerHeight, null);

		g2d.dispose();

		return newImage;
	}

	public static int getFontPixelSize(int fontSize) {
		return (int) (72.0 * fontSize / 96);
	}

	public static Image convertToFXImage(BufferedImage bufferedImage) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			ImageIO.write(bufferedImage, "png", outputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		return new Image(inputStream);
	}

	public static BufferedImage convertToBufferedImage(Image image) {
		return SwingFXUtils.fromFXImage(image, null);
	}
}