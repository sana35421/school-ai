package com.schoolai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolai.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    @Select("SELECT id, file_name, file_type, file_size, storage_path, status, dify_doc_id, uploaded_by, uploaded_at "
            + "FROM kb_documents ORDER BY uploaded_at DESC")
    List<KbDocument> selectAllOrdered();
}
