package me.catand.trpgbot.plugins;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.bg.CircleBackground;
import com.kennycason.kumo.font.KumoFont;
import com.kennycason.kumo.font.scale.LinearFontScalar;
import com.kennycason.kumo.image.AngleGenerator;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.nlp.tokenizer.api.WordTokenizer;
import com.kennycason.kumo.palette.ColorPalette;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.catand.trpgbot.entity.WordCloudEntity;
import me.catand.trpgbot.repository.WordCloudRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Shiro
@Component
public class WordCloudPlugin {

	// 下面这些final变量按说都可以做成配置的
	// 但是我是懒狗! >:^)
	// 需要改的时候直接改此处源码重新编译
	// 配置文件以后再说
	private static final String ZONE = "Asia/Shanghai";
	private static final long botQq = 1789937107L;
	// 定时任务推送速率（5秒一个群）
	private static final int CRON_TASK_RATE = 5;
	// 最小字号
	private static final int MAX_FONT_SIZE = 80;
	// 最大字号
	private static final int MIN_FONT_SIZE = 20;
	// 过滤规则 匹配方式为 contains
	private static final List<String> FILTER_RULE = new ArrayList<>(Arrays.asList("http", "&#"));
	private static final List<Long> ADMIN_LIST = new ArrayList<>(Arrays.asList(3047354896L, 1831901504L));
	private static final String WORD_CLOUD = "^(我的|本群)(今日|本周|本月|本年)词云";
	private static final String WORD_CLOUD_CRON = "^词云\\s(day|week|month)";

	@Resource
	private BotContainer botContainer;

	@Autowired
	private WordCloudRepository repository;

	@GroupMessageHandler
	public void saveMsg(GroupMessageEvent event) {
		WordCloudEntity data = new WordCloudEntity(0, event.getUserId(), event.getGroupId(), event.getMessage(), System.currentTimeMillis() / 1000);
		repository.save(data);
	}

	private String generateWordCloud(List<String> contents) {
		FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
		frequencyAnalyzer.setWordFrequenciesToReturn(300);
		frequencyAnalyzer.setMinWordLength(2);
		frequencyAnalyzer.setWordTokenizer(new JieBaTokenizer());
		List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(contents);
		Dimension dimension = new Dimension(1000, 1000);
		WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
		wordCloud.setPadding(2);
		wordCloud.setAngleGenerator(new AngleGenerator(0));
		wordCloud.setKumoFont(
				new KumoFont(WordCloudPlugin.class.getResourceAsStream("/fonts/NotoSansSC-Black.ttf"))
		);
		List<String> colors = new ArrayList<>(Arrays.asList("0000FF", "40D3F1", "40C5F1", "40AAF1", "408DF1", "4055F1"));
		wordCloud.setBackground(new CircleBackground(((1000 + 1000) / 4)));
		wordCloud.setBackgroundColor(new Color(0xFFFFFF));
		List<Color> colorList = colors.stream()
				.map(color -> Integer.parseInt(color, 16))
				.map(Color::new)
				.toList();
		ColorPalette colorPalette = new ColorPalette(colorList.toArray(new Color[0]));
		wordCloud.setColorPalette(colorPalette);

		LinearFontScalar fontScalar = new LinearFontScalar(
				MIN_FONT_SIZE, MAX_FONT_SIZE
		);
		wordCloud.setFontScalar(fontScalar);

		wordCloud.build(wordFrequencies);

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		wordCloud.writeToStreamAsPNG(stream);

		return Base64.getEncoder().encodeToString(stream.toByteArray());
	}

	private List<String> query(long userId, long groupId, Long start, Long end) {
		return repository.findAllBySenderIdAndGroupIdAndTimeBetween(userId, groupId, start, end)
				.stream()
				.map(WordCloudEntity::getContent)
				.collect(Collectors.toList());
	}

	private List<String> query(long groupId, Long start, Long end) {
		return repository.findAllByGroupIdAndTimeBetween(groupId, start, end)
				.stream()
				.map(WordCloudEntity::getContent)
				.collect(Collectors.toList());
	}

	private List<String> getWordsForRange(long userId, long groupId, String type, String range) {
		long nowTimestamp = Instant.now().getEpochSecond();
		long dayInSeconds = 24 * 60 * 60;
		long weekInSeconds = 7 * dayInSeconds;
		long monthInSeconds = 30 * dayInSeconds;
		long yearInSeconds = 365 * dayInSeconds;

		long startOfDayTimestamp = nowTimestamp - dayInSeconds;
		long startOfWeekTimestamp = nowTimestamp - weekInSeconds;
		long startOfMonthTimestamp = nowTimestamp - monthInSeconds;
		long startOfYearTimestamp = nowTimestamp - yearInSeconds;

		switch (type) {
			case "我的":
				switch (range) {
					case "今日":
						return query(userId, groupId, startOfDayTimestamp, nowTimestamp);
					case "本周":
						return query(userId, groupId, startOfWeekTimestamp, nowTimestamp);
					case "本月":
						return query(userId, groupId, startOfMonthTimestamp, nowTimestamp);
					case "本年":
						return query(userId, groupId, startOfYearTimestamp, nowTimestamp);
				}
				break;
			case "本群":
				switch (range) {
					case "今日":
						return query(groupId, startOfDayTimestamp, nowTimestamp);
					case "本周":
						return query(groupId, startOfWeekTimestamp, nowTimestamp);
					case "本月":
						return query(groupId, startOfMonthTimestamp, nowTimestamp);
					case "本年":
						return query(groupId, startOfYearTimestamp, nowTimestamp);
				}
				break;
		}
		return new ArrayList<>();
	}

	private List<String> getWords(long userId, long groupId, String type, String range) {
		String filterRule = String.join("|", FILTER_RULE);
		Pattern pattern = Pattern.compile(filterRule);
		List<String> contents = new ArrayList<>();
		for (String raw : getWordsForRange(userId, groupId, type, range)) {
			List<ArrayMsg> rawMessages = ShiroUtils.rawToArrayMsg(raw);
			for (ArrayMsg msg : rawMessages) {
				if (msg.getType().equals(MsgTypeEnum.text)) {
					String text = msg.getData().get("text").trim();
					if (!pattern.matcher(text).matches()) {
						contents.add(text);
					}
				}
			}
		}
		return contents;
	}

	@GroupMessageHandler
	@MessageHandlerFilter(cmd = WORD_CLOUD)
	public void handler(GroupMessageEvent event, Bot bot, Matcher matcher) {
		int msgId = event.getMessageId();
		try {
			String type = matcher.group(1);
			String range = matcher.group(2);
			List<String> contents = getWords(event.getUserId(), event.getGroupId(), type, range);
			if (contents.isEmpty()) {
				throw new RuntimeException("屁话没说过 >:(");
			}
			String msg = MsgUtils.builder().reply(msgId).img("base64://" + generateWordCloud(contents)).build();
			bot.sendGroupMsg(event.getGroupId(), msg, false);
		} catch (Exception e) {
			bot.sendGroupMsg(event.getGroupId(), "生成词云失败: " + e.getMessage(), false);
		}
	}

	@AnyMessageHandler
	@MessageHandlerFilter(cmd = WORD_CLOUD_CRON)
	public void handler(AnyMessageEvent event, Bot bot, Matcher matcher) {
		if (!ADMIN_LIST.contains(event.getUserId())) {
			bot.sendMsg(event, "此操作需要管理员权限", false);
		}
		switch (matcher.group(1)) {
			case "day":
				taskForDay();
				break;
			case "week":
				taskForWeek();
				break;
			case "month":
				taskForMonth();
				break;
		}
	}

	@Scheduled(cron = "0 30 23 * * ?", zone = ZONE)
	public void taskForDay() {
		LocalDateTime now = LocalDateTime.now();
		// 跳过周日
		if (now.getDayOfWeek() == DayOfWeek.SUNDAY) return;
		// 跳过每月最后一天
		if (now.equals(now.with(TemporalAdjusters.lastDayOfMonth()))) return;
		task("今日");
	}

	@Scheduled(cron = "0 30 23 ? * SUN", zone = ZONE)
	public void taskForWeek() {
		LocalDateTime now = LocalDateTime.now();
		// 跳过每月最后一天
		if (now.equals(now.with(TemporalAdjusters.lastDayOfMonth()))) return;
		task("本周");
	}

	@Scheduled(cron = "0 30 23 L * ?", zone = ZONE)
	public void taskForMonth() {
		task("本月");
	}

	private void task(String range) {
		Bot bot = botContainer.robots.get(botQq);
		long cronTaskRate = CRON_TASK_RATE * 1000L;
		for (GroupInfoResp data : bot.getGroupList().getData()) {
			try {
				Thread.sleep(cronTaskRate);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			List<String> contents = getWords(0L, data.getGroupId(), "本群", range);
			if (contents.isEmpty()) {
				continue;
			}
			bot.sendGroupMsg(data.getGroupId(), "看看你们" + range + "聊了些什么东西\uD83D\uDC40", false);
			String msg = MsgUtils.builder().img("base64://" + generateWordCloud(contents)).build();
			bot.sendGroupMsg(data.getGroupId(), msg, false);
			log.info(range + "词云推送到群 [" + data.getGroupName() + "](" + data.getGroupId() + ") 成功");
		}
	}

	private static class JieBaTokenizer implements WordTokenizer {
		@Override
		public List<String> tokenize(String sentence) {
			return new JiebaSegmenter().process(sentence, JiebaSegmenter.SegMode.INDEX).stream().map(segToken -> segToken.word.trim()).collect(Collectors.toList());
		}
	}
}