package me.catand.trpgbot.repository;

import me.catand.trpgbot.entity.PairMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PairMemberRepository extends JpaRepository<PairMemberEntity, Integer> {


    @Transactional
    @Override
    <S extends PairMemberEntity> S save(S entity);
    List<PairMemberEntity> findByGroupIdAndQqNumber(long groupId, long qqNumber);
    List<PairMemberEntity> findByGroupIdAndPairedMemberQq(long groupId, long pairedMemberQq);
}