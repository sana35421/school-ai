package com.schoolai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolai.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("SELECT * FROM conversations WHERE user_id = #{userId} AND last_active_at >= #{since} ORDER BY last_active_at DESC")
    List<Conversation> findRecentByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM conversations WHERE user_id IN (#{userIds}) AND last_active_at >= #{since} ORDER BY last_active_at DESC")
    List<Conversation> findByUserIds(@Param("userIds") Collection<Long> userIds, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM conversations WHERE conversation_id = #{conversationId}")
    Conversation findByConversationId(@Param("conversationId") String conversationId);

    @Update("UPDATE conversations SET last_active_at = NOW() WHERE conversation_id = #{conversationId}")
    int updateLastActive(@Param("conversationId") String conversationId);

    @Select("SELECT user_id FROM conversations WHERE conversation_id = #{conversationId}")
    Long findUserIdByConversationId(@Param("conversationId") String conversationId);
}