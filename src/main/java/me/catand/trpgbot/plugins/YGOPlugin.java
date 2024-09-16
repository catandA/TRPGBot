package me.catand.trpgbot.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.image.WritableImage;
import me.catand.trpgbot.utils.ImageUtil;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class YGOPlugin extends BotPlugin {
	private static final Pattern PATTERN = Pattern.compile("\"name\":\"(.*?)\",\"recent_time\":\".*?count\":\"(.*?)\",\".*?,\"win\":\"(.*?)\",\"draw\":\".*?\",\"lose\":\"(.*?)\"},.*?\"win\":\"(.*?)\",\"draw\":\".*?\",\"lose\":\"(.*?)\"");

	@Override
	public int onGroupMessage(Bot bot, GroupMessageEvent event) {
		MsgUtils sendMsg = new MsgUtils();
		List<String> arrayMsg = event.getArrayMsg().stream()
				.filter(msg -> msg.getType() == MsgTypeEnum.text)
				.map(ArrayMsg::getData)
				.map(map -> map.get("text"))
				.map(map -> map.replaceAll("\\s+", " "))
				.toList();
		if (arrayMsg.isEmpty() || !arrayMsg.get(0).startsWith("游戏王")) {
			return MESSAGE_IGNORE;
		} else {
			String command = arrayMsg.getFirst().substring(3);
			switch (command) {
				case "饼图":
					try {
						final Scene[] scene = new Scene[1];
						Platform.runLater(() -> {
							try {
								scene[0] = createScene();
							} catch (Exception e) {
								e.printStackTrace();
							}
						});

						String webData = getWebSourceCode("https://sapi.moecube.com:444/ygopro/analytics/deck/type?type=day&source=mycard-athletic");

						Matcher matcher = PATTERN.matcher(webData);

						List<MatchResult> resultMatch = matcher.results().toList();

						ObservableList<PieChart.Data> dataset = FXCollections.observableArrayList();

						if (!resultMatch.isEmpty()) {
							int total = resultMatch.stream().mapToInt(result -> Integer.parseInt(result.group(2))).sum();
							int threshold = (int) (total * 0.02); // 设定阈值为总数的5%
							int otherCount = 0;

							for (MatchResult result : resultMatch) {
								int count = Integer.parseInt(result.group(2));
								if (count < threshold) {
									otherCount += count;
								} else {
									double percentage = (count * 100.0) / total;
									dataset.add(new PieChart.Data(result.group(1) + " " + String.format("%.2f%%", percentage), count));
								}
							}

							if (otherCount > 0) {
								double otherPercentage = (otherCount * 100.0) / total;
								dataset.add(new PieChart.Data("其他 " + String.format("%.2f%%", otherPercentage), otherCount));
							}
						}

						// 等待 JavaFX 线程完成初始化
						while (scene[0] == null) {
							Thread.sleep(100);
						}


						PieChart pieChart = (PieChart) scene[0].lookup("#pieChart");
						pieChart.setData(dataset);
						pieChart.setLabelsVisible(true);
						pieChart.setLegendVisible(false);
						pieChart.setLabelLineLength(10); // 设置标签线的长度
						// 创建一个 CSS 样式字符串
						String pieChartLabelStyle = ".chart-pie-label { -fx-font-family: 'Noto Sans SC'; -fx-fill: #d4d4d4; }";

						// 将该样式应用到 PieChart 的标签上
						scene[0].getStylesheets().add("data:text/css," + pieChartLabelStyle);

						// 发送消息
						final WritableImage[] writableImage = new WritableImage[1];
						Platform.runLater(() -> {
							try {
								writableImage[0] = scene[0].snapshot(null);
								sendMsg.img(ImageUtil.ImageToBase64(SwingFXUtils.fromFXImage(writableImage[0], null)));
								bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						});

					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
			}
			return MESSAGE_BLOCK;
		}
	}


	private Scene createScene() {
		try {
			Parent root = FXMLLoader.load(YGOPlugin.class.getResource("/scene/YGOPie.fxml"));
			Scene scene = new Scene(root, 600, 600);
			return scene;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getWebSourceCode(String urlString) throws Exception {
		URL url = new URL(urlString);
		return IOUtils.toString(url, StandardCharsets.UTF_8);
	}
}