package com.schoolai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolai.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> findByConversationId(@Param("conversationId") String conversationId);

    @Select("<script>"
            + "SELECT * FROM messages WHERE conversation_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<Message> findByConversationIds(@Param("ids") Collection<String> conversationIds);
}