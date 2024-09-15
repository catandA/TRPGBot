package me.catand.trpgbot.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;
import me.catand.trpgbot.entity.PairMemberEntity;
import me.catand.trpgbot.repository.PairMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static me.catand.trpgbot.utils.DateUtils.isUnixTimeToday;

// D20投出了[1]!
@Component
public class PairPlugin extends BotPlugin {
	@Autowired
	private WordCloudPlugin wordCloudPlugin;
	@Autowired
	private PairMemberRepository pairMemberRepository;

	@Override
	public int onGroupMessage(Bot bot, GroupMessageEvent event) {
		MsgUtils sendMsg = new MsgUtils();
		List<String> arrayMsg = event.getArrayMsg().stream()
				.filter(msg -> msg.getType() == MsgTypeEnum.text)
				.map(ArrayMsg::getData)
				.map(map -> map.get("text"))
				.map(map -> map.replaceAll("\\s+", " "))
				.toList();
		if (arrayMsg.isEmpty() || !arrayMsg.getFirst().equals("!marry") && !(arrayMsg.getFirst().equals("！marry"))) {
			return MESSAGE_IGNORE;
		} else {
			List<PairMemberEntity> pairMemberEntities = pairMemberRepository.findByGroupIdAndQqNumber(event.getGroupId(), event.getUserId());
			List<PairMemberEntity> pairedMemberEntities = pairMemberRepository.findByGroupIdAndPairedMemberQq(event.getGroupId(), event.getUserId());
			if (!pairMemberEntities.isEmpty() && isUnixTimeToday(pairMemberEntities.getFirst().getPairDate())) {
				sendMsg.at(event.getUserId()).text("  你今天已经娶了  ").at(pairMemberEntities.getFirst().getPairedMemberQq()).img("https://q1.qlogo.cn/g?b=qq&nk=" + pairMemberEntities.getFirst().getPairedMemberQq() + "&s=100");
			} else if (!pairedMemberEntities.isEmpty() && isUnixTimeToday(pairedMemberEntities.getFirst().getPairDate())) {
				sendMsg.at(event.getUserId()).text("  你今天已经被  ").at(pairedMemberEntities.getFirst().getQqNumber()).img("https://q1.qlogo.cn/g?b=qq&nk=" + pairedMemberEntities.getFirst().getQqNumber() + "&s=100").text("娶了");
			} else {
				// 今天没有配对
				List<Long> activeMembers = wordCloudPlugin.queryActiveMembers(event.getGroupId());
				if (activeMembers.size() < 2) {
					sendMsg.text("这群全是死人 >:^(");
				} else {
					// 随机选取两个成员
					Long qqNumber = activeMembers.get((int) (Math.random() * activeMembers.size()));
					// 防止其他已经配对的成员被选中,如果有配对则从列表中删除加快随机速度
					while (qqNumber.equals(event.getUserId())
							|| !pairMemberRepository.findByGroupIdAndQqNumber(event.getGroupId(), qqNumber).isEmpty()
							|| !pairMemberRepository.findByGroupIdAndPairedMemberQq(event.getGroupId(), qqNumber).isEmpty()) {
						activeMembers.remove(qqNumber);
						if (activeMembers.isEmpty()) {
							sendMsg.text("你不许配对, 没有单身人 >:^)");
							bot.sendGroupMsg(event.getGroupId(), sendMsg.build(), false);
							return MESSAGE_BLOCK;
						}
						qqNumber = activeMembers.get((int) (Math.random() * activeMembers.size()));
					}
					PairMemberEntity pairMemberEntity = new PairMemberEntity();
					pairMemberEntity.setGroupId(event.getGroupId());
					pairMemberEntity.setQqNumber(event.getUserId());
					pairMemberEntity.setPairedMemberQq(qqNumber);
					pairMemberEntity.setPairDate(Instant.now().getEpochSecond());
					pairMemberRepository.save(pairMemberEntity);
					sendMsg.at(event.getUserId()).text("  你娶了  ").at(qqNumber).img("https://q1.qlogo.cn/g?b=qq&nk=" + qqNumber + "&s=100");
				}
			}
			bot.sendGroupMsg(event.getGroupId(), sendMsg.build(), false);
			return MESSAGE_BLOCK;
		}
	}
}
