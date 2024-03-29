package me.catand.trpgbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "word_cloud")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WordCloudEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Integer id;

	@Column(nullable = false)
	Long senderId;

	@Column(nullable = false)
	Long groupId;

	@Column(nullable = false, columnDefinition = "longtext")
	String content;

	@Column(nullable = false)
	Long time;
}