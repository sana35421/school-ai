package com.schoolai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolai.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE student_id = #{studentId}")
    User findByStudentId(String studentId);

    @Select("SELECT * FROM users WHERE class_id = #{classId}")
    List<User> findByClassId(Long classId);

    @Select("SELECT id FROM users WHERE class_id = #{classId}")
    List<Long> findIdsByClassId(Long classId);
}