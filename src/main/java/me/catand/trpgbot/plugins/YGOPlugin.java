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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.catand.trpgbot.utils.ImageUtil;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
		if (arrayMsg.isEmpty()) {
			return MESSAGE_IGNORE;
		} else {
			int index = arrayMsg.getFirst().indexOf(" ");
			String command;
			String arg;
			if (index == -1) {
				command = arrayMsg.getFirst();
				arg = "";
			} else {
				command = arrayMsg.getFirst().substring(0, index);
				arg = arrayMsg.getFirst().substring(index + 1);
			}
			try {
				switch (command) {
					case "游戏王饼图": {
						final Scene[] scene = new Scene[1];
						Platform.runLater(() -> {
							try {
								scene[0] = createScene("YGOPie");
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
						break;
					}

					case "查卡":
					case "卡查": {
						if (arg.isEmpty() || arg.isBlank()) {
							sendMsg.text("请输入要查询的卡名");
							bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
							break;
						}

						// 更新用户数据
						UserSearchData userSearchData = updateUserList(event.getUserId());
						userSearchData.setUserSearchContent(arg);
						userSearchData.setUserSearchPage(1);
						userSearchData.setUserSearchProcess(0);
						userSearchDataList.add(userSearchData);

						String returnMsg = searchCard(arg, 0, 0, "");

						if (returnMsg.equals("只有一张卡")) {
							UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

							// 修改用户数据并添加到列表
							isInUserList.setUserSearchCard(1);
							isInUserList.setUserSearchProcess(1);
							userSearchDataList.add(isInUserList);

							handleMessage(searchCard(arg, 0, 1, ""), sendMsg);
						} else {
							handleMessage(returnMsg, sendMsg);
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					// 翻页
					case "上一页": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.getUserSearchContent().isEmpty()) {
							sendMsg.text("null");
						} else if (isInUserList.getUserSearchProcess() == 1) { // 如果是在单卡查询里了
							if (isInUserList.getUserSearchPage() > 1) {
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(isInUserList.getUserSearchCard() - 1);
								isInUserList.setUserSearchProcess(1);
								userSearchDataList.add(isInUserList);

								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.getUserSearchContent(), (isInUserList.getUserSearchPage() - 2) * 10, isInUserList.getUserSearchCard(), ""));
							}
						} else { // 翻页
							int page = isInUserList.getUserSearchPage();
							if (page > 1) {
								isInUserList.setUserSearchPage(page - 1);
								userSearchDataList.add(isInUserList);
								sendMsg.text(searchCard(isInUserList.getUserSearchContent(), (page - 2) * 10, 0, ""));
							} else {
								userSearchDataList.add(isInUserList);
								sendMsg.text("已经是第一页了");
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					// 下一页
					case "下一页": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.getUserSearchContent().isEmpty()) {
							sendMsg.text("null");
						} else if (isInUserList.getUserSearchProcess() == 1) { // 如果是在单卡查询里了
							if (isInUserList.getUserSearchPage() < 10) {
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(isInUserList.getUserSearchCard() + 1);
								isInUserList.setUserSearchProcess(1);
								userSearchDataList.add(isInUserList);
								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.getUserSearchContent(), (isInUserList.getUserSearchPage() - 1) * 10, isInUserList.getUserSearchCard(), ""));
							}
						} else { // 翻页
							int page = isInUserList.getUserSearchPage();
							String content = searchCard(isInUserList.getUserSearchContent(), page * 10, 0, "");

							// 是否有内容，没有则是最后一页
							if (content.equals("没有找到相关的东西")) {
								userSearchDataList.add(isInUserList);
								sendMsg.text("已经是最后一页了");
							} else {
								isInUserList.setUserSearchPage(page + 1);
								userSearchDataList.add(isInUserList);
								sendMsg.text(content);
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					// 进入单卡
					case "进入单卡 ": {
						Pattern pattern = Pattern.compile("进入单卡 (\\d+)");
						Matcher matchResult = pattern.matcher(arg);
						if (!matchResult.find()) sendMsg.text("null");
						int cardNumber = Integer.parseInt(matchResult.group(1));

						if (cardNumber < 11) {
							UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

							// 不存在则返回
							if (isInUserList.getUserSearchContent().isEmpty()) {
								sendMsg.text("null");
							} else {
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(cardNumber);
								isInUserList.setUserSearchProcess(1);
								userSearchDataList.add(isInUserList);
								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.getUserSearchContent(), (isInUserList.getUserSearchPage() - 1) * 10, isInUserList.getUserSearchCard(), ""));
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					// 退出单卡
					case "返回":
					case "退出": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.getUserSearchContent().isEmpty()) {
							sendMsg.text("null");
						} else if (isInUserList.getUserSearchProcess() == 1) { // 如果是在单卡查询里了
							// 修改用户数据并添加到列表
							isInUserList.setUserSearchCard(0);
							isInUserList.setUserSearchProcess(0);
							userSearchDataList.add(isInUserList);
							// 返回单卡数据
							sendMsg.text(searchCard(isInUserList.getUserSearchContent(), (isInUserList.getUserSearchPage() - 1) * 10, 0, ""));
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}
					case "卡片收录": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.userSearchContent.equals("")) {
							sendMsg.text("null");
						} else if (isInUserList.userSearchProcess == 1) { // 如果是在单卡查询里了
							// 修改用户数据并添加到列表
							userSearchDataList.add(isInUserList);
							// 返回单卡数据
							sendMsg.text(searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "md卡包") + "\n" +
									searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "ocg收录"));
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "卡片收录 ": {
						Matcher matchResult = Pattern.compile("卡片收录 (\\d*)").matcher(arg);
						if (!matchResult.find()) sendMsg.text("null");
						String cardNumber = matchResult.group(1);

						if (Integer.parseInt(cardNumber) < 11) {
							UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

							// 不存在则返回
							if (isInUserList.userSearchContent.equals("")) {
								sendMsg.text("null");
							} else {
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(Integer.parseInt(cardNumber));
								isInUserList.setUserSearchProcess(0);
								userSearchDataList.add(isInUserList);
								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "md卡包") + "\n" +
										searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "ocg收录"));
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "卡片调整": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.userSearchContent.equals("")) {
							sendMsg.text("null");
						} else {
							// 修改用户数据并添加到列表
							userSearchDataList.add(isInUserList);
							String returnMessage = searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "日文调整") +
									"{forwardmessage的分割符}" +
									searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "日文faq");
							if (returnMessage.length() > 5000) {
								// 查卡
								String cardToSearchInURL = URLEncoder.encode(isInUserList.userSearchContent, "UTF-8").replace(" ", "+");
								String WebData = getWebSourceCode("https://ygocdb.com/more?search=" + cardToSearchInURL + "&start=" + ((isInUserList.userSearchPage - 1) * 10));

								// 使用正则表达式进行匹配
								Matcher resultMatch = Pattern.compile("<h3><span>(\\d*?)</span>&nbsp;[\\s\\S]*?<strong class=\"name\"><span>(.*?)</span><br></strong>.*\\s.*\\s*(.*)").matcher(WebData);
								List<MatchResult> matchResults = new ArrayList<>();

								// 将匹配结果添加到列表中
								while (resultMatch.find()) {
									matchResults.add(resultMatch.toMatchResult());
								}

								// 检查是否找到任何结果
								if (matchResults.isEmpty()) {
									sendMsg.text("没有找到相关的东西");
								} else {
									// 获取 WebDataCard 的数据
									String WebDataCard = getWebSourceCode("https://ygocdb.com/card/" + matchResults.get(isInUserList.userSearchCard - 1).group(1));

									// 使用新的正则表达式匹配 QAUrl
									Matcher QAUrlMatcher = Pattern.compile("title=\"数据库编号\">(\\d*)</span>").matcher(WebDataCard);

									// 检查 QAUrlMatcher 是否找到匹配
									if (QAUrlMatcher.find()) {
										// 构建返回信息
										returnMessage = "长度太长，超出QQ发送字数限制了，自己去官网看吧\n网址：https://www.db.yugioh-card.com/yugiohdb/faq_search.action?ope=4&cid=" + QAUrlMatcher.group(1) + "&request_locale=ja";
									} else {
										sendMsg.text("没有找到数据库编号信息");
									}
								}

							}
							// 返回单卡数据
							sendMsg.text(returnMessage);
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "卡片调整 ": {
						Matcher matchResult = Pattern.compile("卡片调整 (\\d*)").matcher(arg);
						if (!matchResult.find()) sendMsg.text("null");
						String cardNumber = matchResult.group(1);

						if (Integer.parseInt(cardNumber) < 11) {
							UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

							// 不存在则返回
							if (isInUserList.userSearchContent.equals("")) {
								sendMsg.text("null");
							} else {
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(Integer.parseInt(cardNumber));
								isInUserList.setUserSearchProcess(0);
								userSearchDataList.add(isInUserList);

								String returnMessage = searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "日文调整") +
										"{forwardmessage的分割符}" +
										searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "日文faq");
								if (returnMessage.length() > 5000) {
									// 查卡
									String cardToSearchInURL = URLEncoder.encode(isInUserList.userSearchContent, "UTF-8").replace(" ", "+");
									String WebData = getWebSourceCode("https://ygocdb.com/more?search=" + cardToSearchInURL + "&start=" + ((isInUserList.userSearchPage - 1) * 10));

									// 使用正则表达式进行匹配
									Matcher resultMatch = Pattern.compile("<h3><span>(\\d*?)</span>&nbsp;[\\s\\S]*?<strong class=\"name\"><span>(.*?)</span><br></strong>.*\\s.*\\s*(.*)").matcher(WebData);

									// 创建一个列表来存储匹配结果
									List<MatchResult> matchResults = new ArrayList<>();

									// 将匹配结果添加到列表中
									while (resultMatch.find()) {
										matchResults.add(resultMatch.toMatchResult());
									}

									// 检查是否找到任何结果
									if (matchResults.isEmpty()) {
										sendMsg.text("没有找到相关的东西");
									} else {
										// 获取 WebDataCard 的数据
										String WebDataCard = getWebSourceCode("https://ygocdb.com/card/" + matchResults.get(isInUserList.userSearchCard - 1).group(1));

										// 使用新的正则表达式匹配 QAUrl
										Matcher QAUrlMatcher = Pattern.compile("title=\"数据库编号\">(\\d*)</span>").matcher(WebDataCard);

										// 检查 QAUrlMatcher 是否找到匹配
										if (QAUrlMatcher.find()) {
											// 构建返回信息
											returnMessage = "长度太长，超出QQ发送字数限制了，自己去官网看吧\n网址：https://www.db.yugioh-card.com/yugiohdb/faq_search.action?ope=4&cid=" + QAUrlMatcher.group(1) + "&request_locale=ja";
										} else {
											sendMsg.text("没有找到数据库编号信息");
										}
									}


								}

								// 返回单卡数据
								sendMsg.text(returnMessage);
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "卡片价格": {
						UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

						// 不存在则返回
						if (isInUserList.userSearchContent.equals("")) {
							sendMsg.text("null");
						} else {
							// 修改用户数据并添加到列表
							userSearchDataList.add(isInUserList);
							// 返回单卡数据
							sendMsg.text(searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "查价格"));
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "卡片价格 ": {
						Matcher matchResult = Pattern.compile("卡片价格 (\\d*)").matcher(arg);
						if (!matchResult.find()) sendMsg.text("null");
						String cardNumber = matchResult.group(1);

						if (Integer.parseInt(cardNumber) < 11) {
							UserSearchData isInUserList = updateUserList(event.getUserId()); // 获得用户数据

							// 不存在则返回
							if (isInUserList.userSearchContent.equals("")) {
								sendMsg.text("null");
							} else if (isInUserList.userSearchProcess == 1) { // 如果是在单卡查询里了
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(Integer.parseInt(cardNumber));
								isInUserList.setUserSearchProcess(0);
								userSearchDataList.add(isInUserList);
								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "查价格"));
							} else { // 翻页
								// 修改用户数据并添加到列表
								isInUserList.setUserSearchCard(Integer.parseInt(cardNumber));
								isInUserList.setUserSearchProcess(0);
								userSearchDataList.add(isInUserList);
								// 返回单卡数据
								sendMsg.text(searchCard(isInUserList.userSearchContent, (isInUserList.userSearchPage - 1) * 10, isInUserList.userSearchCard, "查价格"));
							}
						}
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					case "释放卡查内存": {
						userSearchDataList.clear();
						sendMsg.text("释放自我！");
						bot.sendGroupMsg(event.getGroupId(), event.getUserId(), sendMsg.build(), false);
						break;
					}

					default:
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}


			return MESSAGE_BLOCK;
		}
	}

	public String searchCard(String cardToSearch, int page, int cardNumber, String additionalInfo) throws Exception {
		// 查卡
		String cardToSearchInURL = URLEncoder.encode(cardToSearch, StandardCharsets.UTF_8).replace(" ", "+");

		String webData = getWebSourceCode("https://ygocdb.com/more?search=" + cardToSearchInURL + "&start=" + page);

		List<Matcher> cardIdResultMatch = findAllMatchers(webData, "<h3><span>(\\d*)</span>&nbsp;");
		List<Matcher> resultMatch = findAllMatchers(webData, "<h3><span>(\\d*?)</span>&nbsp;[\\s\\S]*?<strong class=\"name\"><span>(.*?)</span><br></strong>.*\\s.*\\s*(.*)");

		if (resultMatch.isEmpty()) {
			return "没有找到相关的东西";
		}

		// 输出文本
		StringBuilder outputResult = new StringBuilder();
		int resultNumber = 1;

		// 如果是查单卡或者只搜到了一张卡
		if (cardNumber != 0) {
			// 如果有特殊指令
			if (!additionalInfo.isEmpty()) {
				// 屎山遗留，加个if来判断是不是查卡片价格
				if (additionalInfo.equals("查价格")) {
					return searchCardPrices(resultMatch.get(cardNumber - 1).group(2));
				}
				return additionalCommandProcess(resultMatch.get(cardNumber - 1).group(1), additionalInfo);
			}
			return searchCardInfo(resultMatch, cardNumber);
		}

		if (page == 0 && resultMatch.size() == 1) {
			return "只有一张卡";
		}

		// 输出列表
		for (Matcher matcher : resultMatch) {
			// 只输出10张
			if (resultNumber == 11) {
				return outputResult.toString();
			}

			// 处理���配数据
			outputResult.append(resultNumber).append("：").append(matcher.group(2)).append("\n类型：");

			Matcher monsterMatch = Pattern.compile("(.*?)<br>(.*)").matcher(matcher.group(3));
			if (monsterMatch.find()) {
				outputResult.append(monsterMatch.group(1)).append("\n身板：").append(monsterMatch.group(2));
			} else {
				outputResult.append(matcher.group(3));
			}

			outputResult.append("\n卡片密码：").append(cardIdResultMatch.get(resultNumber - 1).group(1));
			outputResult.append("{forwardmessage的图片}:").append(cardIdResultMatch.get(resultNumber - 1).group(1));
			outputResult.append("{forwardmessage的分割符}");

			resultNumber++;
		}
		return outputResult.append("卡片列表").toString();
	}

	public String additionalCommandProcess(String cardNumber, String additionalInfo) throws Exception {
		String returnMsg = "null";

		if (additionalInfo.equals("md卡包")) {
			String webData = getWebSourceCode("https://www.ourocg.cn/search/" + cardNumber);
			List<Matcher> resultMatch = findAllMatchers(webData, "<tr><td><a href=\"/md_package/(\\d*)\\D*?>(.*?)<br/><small>(.*?)</small></a></td><td style=\"text-align:center\">(.*?)</td></tr>");
			if (!resultMatch.isEmpty()) {
				returnMsg = "MD收录卡包:";
				for (Matcher matcher : resultMatch) {
					returnMsg += "\n卡包编号:" + matcher.group(1) + " 日文名:" + matcher.group(2) + " 英文名:" + matcher.group(3) + " 罕贵度:" + matcher.group(4);
				}
			}
		}

		if (additionalInfo.equals("ocg收录")) {
			String webData = getWebSourceCode("https://ygocdb.com/card/" + cardNumber);
			List<Matcher> resultMatch = findAllMatchers(webData, "<li class=\"pack\">[\\s\\S]*?<span>(.*?)</span><span>(.*?)</span>[\\s\\S]*?<a href=\"[\\s\\S]*?\">(.*?)</a>");
			if (!resultMatch.isEmpty()) {
				returnMsg = "OCG收录卡包:";
				for (Matcher matcher : resultMatch) {
					returnMsg += "\n收录时间:" + matcher.group(1) + " 卡片编号:" + matcher.group(2) + " 收录卡包:" + matcher.group(3);
				}
			}
		}

		if (additionalInfo.equals("日文调整")) {
			String webData = getWebSourceCode("https://ygocdb.com/card/" + cardNumber);
			Matcher resultMatch = findSingleMatcher(webData, "<div class=\"qa supplement\"><ul><li>(.*?)</li></ul></div>");
			if (resultMatch == null) {
				return "没有调整";
			}
			String resultString = resultMatch.group(1).replaceAll("(<a .*?\")", "").replaceAll("(</a>)", "").replace("</li><li>", "\n");
			returnMsg = "卡片调整信息:\n" + resultString;
		}

		if (additionalInfo.equals("日文faq")) {
			String webData = getWebSourceCode("https://ygocdb.com/card/" + cardNumber);
			List<Matcher> questionMatch = findAllMatchers(webData, "<hr>\\s*<div class=\"qa question\">(.*?)</div>");
			List<Matcher> answerMatch = findAllMatchers(webData, "<div class=\"qa answer\">(.*?)</div>");
			if (!answerMatch.isEmpty()) {
				returnMsg = "日文F&Q";
				for (int i = 0; i < answerMatch.size(); i++) {
					String question = questionMatch.get(i).group(1).replaceAll("(<a .*?\")", "").replaceAll("(</a>)", "");
					String answer = answerMatch.get(i).group(1).replaceAll("(<a .*?\")", "").replaceAll("(</a>)", "").replace("<br>", "");
					returnMsg += "提问：" + question + "\n\n回答：" + answer + "{forwardmessage的分割符}";
				}
			}
		}

		return returnMsg;
	}

	public String searchCardPrices(String cardName) throws Exception {
		String cardToSearchName = URLEncoder.encode(cardName, "UTF-8");
		String webData = getWebSourceCode("https://api.jihuanshe.com/api/market/search/match-product?keyword=" + cardToSearchName + "&game_key=ygo&game_sub_key=ocg&page=1&type=card_version&token="); // 替换为你要请求的 URL

		Matcher pageNumberMatcher = findSingleMatcher(webData, "\"last_page\":(.*?),\"next_page_url\":\"");
		if (pageNumberMatcher == null) {
			return "没有找到相关的东西";
		}
		int pageNumber = Integer.parseInt(pageNumberMatcher.group(1));

		StringBuilder returnMessage = new StringBuilder();
		for (int i = 0; i < pageNumber; i++) {
			String pageData = getWebSourceCode("https://api.jihuanshe.com/api/market/search/match-product?keyword=" + cardToSearchName + "&game_key=ygo&game_sub_key=ocg&page=" + (i + 1) + "&token=");
			List<Matcher> resultMatch = findAllMatchers(pageData, "\"name_cn\":\"(.*?)\",\"name_origin\":\"(.*?)\"[\\s\\S]*?\"card_id\":(.*?),\"number\":\"(.*?)\",\"rarity\":\"(.*?)\",\"image_url\":\"(.*?)\",\"min_price\":(.*?),\"grade\"");
			for (Matcher matcher : resultMatch) {
				String cardNameA = decodeUnicode(matcher.group(1));
				String cardNameB = decodeUnicode(matcher.group(2));
				String cardPack = decodeUnicode(matcher.group(4));
				String rarity = decodeUnicode(matcher.group(5));
				String lowestPrice = matcher.group(7).replace("\"", "");
				String cardPicture = matcher.group(6).replace("\\", "");

				if (!cardPack.contains("RD/") && (cardNameA.equals(cardName) || cardNameB.equals(cardName))) {
					returnMessage.append("卡名:").append(cardName)
							.append("    最低价格:").append(lowestPrice.equals("null") ? "无成交价格" : lowestPrice)
							.append("\n卡片编号:").append(cardPack)
							.append("    罕贵度:").append(rarity)
							.append("{价格查询的图片}:").append(cardPicture)
							.append("{forwardmessage的分割符}");
				}
			}
		}

		return !returnMessage.isEmpty() ? returnMessage + "集换社价格" : "没有找到相关的东西";
	}

	public String searchCardInfo(List<Matcher> resultMatch, int cardNumber) throws Exception, Exception {
		if (resultMatch.size() < cardNumber) {
			return "已经是最后一张了";
		}

		String cardData2 = getWebSourceCode("https://ygocdb.com/card/" + resultMatch.get(cardNumber - 1).group(1));

		Matcher availMatcher = findSingleMatcher(cardData2, "OCG</i>([\\s\\S]*?)TCG</i>([\\s\\S]*?)</div>");
		if (availMatcher == null) {
			return "获得禁限信息失败";
		}

		String ocgAvailMatch;
		String tcgAvailMatch;

		// OCG禁限情况
		if (!availMatcher.group(1).contains("未发售")) {
			if (availMatcher.group(1).contains("禁止卡")) {
				ocgAvailMatch = "禁止卡";
			} else if (availMatcher.group(1).contains("限制卡")) {
				if (availMatcher.group(1).contains("准限制卡")) {
					ocgAvailMatch = "准限制卡";
				} else {
					ocgAvailMatch = "限制卡";
				}
			} else {
				ocgAvailMatch = "无限制";
			}
		} else {
			ocgAvailMatch = "未在ocg发售";
		}

		// TCG禁限情况
		if (!availMatcher.group(2).contains("未发售")) {
			if (availMatcher.group(2).contains("禁止卡")) {
				tcgAvailMatch = "禁止卡";
			} else if (availMatcher.group(2).contains("限制卡")) {
				if (availMatcher.group(2).contains("准限制卡")) {
					tcgAvailMatch = "准限制卡";
				} else {
					tcgAvailMatch = "限制卡";
				}
			} else {
				tcgAvailMatch = "无限制";
			}
		} else {
			tcgAvailMatch = "未在tcg发售";
		}

		String cardData = getWebSourceCode("https://ygocdb.com/api/v0/?search=" + resultMatch.get(cardNumber - 1).group(1));

		Matcher effectMatch = findSingleMatcher(cardData, "\"pdesc\":\"(.*?)\",\"desc\":\"");
		String pEffect = (effectMatch != null) ? effectMatch.group(1) : "";

		Matcher cardEffectMatch = findSingleMatcher(cardData, "\",\"desc\":\"(.*?)\"},\"data");
		if (cardEffectMatch == null) {
			return "error";
		}
		String cardEffect = cardEffectMatch.group(1);

		Matcher cardJapaneseMatch = findSingleMatcher(cardData, "jp_name\":\"(.*?)\",\"");
		if (cardJapaneseMatch == null) {
			return "error";
		}
		String cardJapanese = cardJapaneseMatch.group(1);

		StringBuilder outputResult = new StringBuilder();
		outputResult.append("中文名：").append(resultMatch.get(cardNumber - 1).group(2))
				.append("\n日文名：").append(cardJapanese)
				.append("\n类型：");

		Matcher monsterMatch = findSingleMatcher(resultMatch.get(cardNumber - 1).group(3), "(.*?)<br>(.*)");
		outputResult.append((monsterMatch == null) ? resultMatch.get(cardNumber - 1).group(3) + "\n" :
				monsterMatch.group(1) + "\n身板：" + monsterMatch.group(2) + "\n");

		outputResult.append("卡片ID：").append(resultMatch.get(cardNumber - 1).group(1))
				.append("\nOCG禁限情况：").append(ocgAvailMatch)
				.append("\nTCG禁限情况：").append(tcgAvailMatch).append("\n");

		if (!pEffect.isEmpty()) {
			outputResult.append("灵摆效果：");
			String[] effects = pEffect.contains("\\r") ? pEffect.split("\\r\\n") : pEffect.split("\\n");
			for (String effect : effects) {
				outputResult.append(effect).append("\n");
			}
			outputResult.append("怪兽效果：\n");
		} else {
			outputResult.append("效果或描述：\n");
		}

		String[] cardEffects = cardEffect.contains("\\r") ? cardEffect.split("\\r\\n") : cardEffect.split("\\n");
		for (String effect : cardEffects) {
			outputResult.append(effect).append("\n");
		}

		outputResult.append("{加入图片}：url：").append(resultMatch.get(cardNumber - 1).group(1));

		return outputResult.toString();
	}

	private List<Matcher> findAllMatchers(String webData, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(webData);
		List<Matcher> results = new ArrayList<>();
		while (matcher.find()) {
			// 创建一个新的 Matcher 对象来存储当前匹配的结果
			Matcher matchResult = pattern.matcher(matcher.group(0));
			matchResult.find(); // 使 matchResult 进入匹配状态
			results.add(matchResult);
		}
		return results;
	}

	private Matcher findSingleMatcher(String text, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher;
		}
		return null;
	}

	private String decodeUnicode(String unicode) throws IOException {
		Properties properties = new Properties();
		properties.load(new StringReader("unicodeString=" + unicode));
		return properties.getProperty("unicodeString");
	}

	private Scene createScene(String fxml) {
		try {
			Parent root = FXMLLoader.load(YGOPlugin.class.getResource("/scene/" + fxml + ".fxml"));
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

	private Set<UserSearchData> userSearchDataList = new HashSet<>();

	// 更新用户数据列表
	public UserSearchData updateUserList(long userID) {
		// 寻找是否正在查询之前的卡片
		for (UserSearchData userSearchData : userSearchDataList) {
			if (userSearchData.getUserQQID() == userID) {
				userSearchDataList.remove(userSearchData);
				// 返回修改完成
				return userSearchData;
			}
		}
		// 创建用户数据
		UserSearchData newUserSearchData = new UserSearchData();
		newUserSearchData.setUserQQID(userID);
		newUserSearchData.setUserSearchContent("");
		newUserSearchData.setUserSearchProcess(0);
		newUserSearchData.setUserSearchPage(1);

		// 添加新用户数据到集合中
		userSearchDataList.add(newUserSearchData);

		return newUserSearchData;
	}

	public static String httpRequest(String uri, String cardName) throws Exception {
		String path = "./data/YGOAssistant/CardImageCache/";
		String filePath = cardName + ".jpg";

		File file1 = new File(path + filePath);
		if (file1.exists()) {
			return path + filePath;
		}

		URL url = new URL(uri);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestProperty("referer", ""); // 这是破解防盗链添加的参数
		conn.addRequestProperty(
				"User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.67"
		);
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5 * 1000);
		InputStream inStream = conn.getInputStream(); // 通过输入流获取图片数据
		readInputStream(inStream, filePath, path);
		return path + filePath;
	}

	public static void readInputStream(InputStream inStream, String filePath, String path) throws Exception {
		File file = new File(path);
		File file1 = new File(path + filePath);
		if (!file.exists()) file.mkdirs();
		if (!file1.exists()) {
			FileOutputStream fos = new FileOutputStream(new File(path + filePath));
			byte[] buffer = new byte[102400];
			int len;
			while ((len = inStream.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
			}
			inStream.close();
			fos.flush();
			fos.close();
		} else {
			inStream.close();
		}
	}

	public void handleMessage(String returnMessage, MsgUtils sendMsg) throws Exception {

		if (returnMessage.contains("{加入图片}：url：")) {
			String[] messageParts = returnMessage.split("\\{加入图片}：url：");

			FileInputStream updateImage = new FileInputStream(
					httpRequest(
							"https://cdn.233.momobako.com/ygopro/pics/" + messageParts[1] + ".jpg!half",
							messageParts[1]
					)
			);
			sendMsg.text(messageParts[0]).img(ImageUtil.convertJpgFileInputStreamToBase64(updateImage));
		} else if (returnMessage.contains("{forwardmessage的分割符}")) {
			String[] messages = returnMessage.split("\\{forwardmessage的分割符}");

			for (String itMsg : messages) {
				if (itMsg.contains("{forwardmessage的图片}:")) {
					String[] messageParts = itMsg.split("\\{forwardmessage的图片}:");
					sendMsg.text(messageParts[0]);

					if (messageParts.length > 1) {
						FileInputStream updateImage = new FileInputStream(
								httpRequest(
										"https://cdn.233.momobako.com/ygopro/pics/" + messageParts[1] + ".jpg!half",
										messageParts[1]
								)
						);
						sendMsg.img(ImageUtil.convertJpgFileInputStreamToBase64(updateImage));
					}
				} else {
					String[] messageParts = itMsg.split("\\{价格查询的图片}:");
					sendMsg.text(messageParts[0]);

					if (messageParts.length > 1) {
						sendMsg.img(messageParts[1]);
					}
				}
			}
		}
	}


	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	public class UserSearchData {
		private long userQQID; // 用于存储用户的QQ ID
		private String userSearchContent; // 用户搜索的内容
		private long userSearchProcess; // 当前查到的位置，0:开始查，1:进入特定单卡
		private int userSearchPage; // 当前搜索的页码
		private int userSearchCard; // 当前搜索的卡片
	}
}