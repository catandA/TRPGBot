package me.catand.trpgbot.repository;

import me.catand.trpgbot.entity.WordCloudEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WordCloudRepository extends JpaRepository<WordCloudEntity, Integer> {

    List<WordCloudEntity> findAllBySenderIdAndGroupIdAndTimeBetween(
            Long senderId, Long groupId, Long start, Long end);

    List<WordCloudEntity> findAllByGroupIdAndTimeBetween(Long groupId, Long start, Long end);

    @Transactional
    @Override
    <S extends WordCloudEntity> S save(S entity);
}