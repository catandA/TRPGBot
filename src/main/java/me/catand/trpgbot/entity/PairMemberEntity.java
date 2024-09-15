package me.catand.trpgbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "pair_member", uniqueConstraints = {@UniqueConstraint(columnNames = {"group_id", "qq_number"})})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PairMemberEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "qq_number", nullable = false)
	private Long qqNumber;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "paired_member_qq")
	private Long pairedMemberQq;

	@Column(name = "pair_time")
	private Long pairDate;
}
