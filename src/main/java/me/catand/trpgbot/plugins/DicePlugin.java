package me.catand.trpgbot.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// D20投出了[1]!
@Component
public class DicePlugin extends BotPlugin {
	private final Pattern pattern = Pattern.compile("\\.(\\d*)d(\\d+)");

	@Override
	public int onGroupMessage(Bot bot, GroupMessageEvent event) {
		MsgUtils sendMsg = new MsgUtils();
		List<String> arrayMsg = event.getArrayMsg().stream()
				.filter(msg -> msg.getType() == MsgTypeEnum.text)
				.map(ArrayMsg::getData)
				.map(map -> map.get("text"))
				.map(map -> map.replaceAll("\\s+", " "))
				.toList();
		if (!arrayMsg.isEmpty()) {
			sendMsg.at(event.getUserId()).text("\n");
			Matcher matcher = pattern.matcher(arrayMsg.getFirst());
			if (matcher.find()) {
				int times;
				if (matcher.group(1).isEmpty()) {
					times = 1;
				} else {
					times = Integer.parseInt(matcher.group(1));
				}
				int max = Integer.parseInt(matcher.group(2));
				if (times == 1) {
					sendMsg.text("D" + max + "投出了[" + (int) (Math.random() * max + 1) + "]");
				} else if (times>40){
					sendMsg.text("你d太多了, 不许");
				}else {
					sendMsg.text(times + "D" + max + "投出了");
					int sum = 0;
					for (int i = 0; i < times; i++) {
						int roll = (int) (Math.random() * max + 1);
						sum += roll;
						sendMsg.text(roll + (i == times - 1 ? "=[" + sum + "]!" : "+"));
					}
				}
			} else {
				return MESSAGE_IGNORE;
			}
			bot.sendGroupMsg(event.getGroupId(), sendMsg.build(), false);
			return MESSAGE_BLOCK;
		} else {
			return MESSAGE_IGNORE;
		}
	}
}
