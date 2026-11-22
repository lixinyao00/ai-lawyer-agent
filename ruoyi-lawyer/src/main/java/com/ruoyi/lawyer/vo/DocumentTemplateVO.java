package com.ruoyi.lawyer.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 文书模板视图对象
 *
 * @author ruoyi
 */
public class DocumentTemplateVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 模板编码 */
    private String code;

    /** 模板名称 */
    private String name;

    /** 模板说明 */
    private String description;

    /** 要素字段定义 */
    private List<FieldDef> fields = new ArrayList<>();

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public List<FieldDef> getFields()
    {
        return fields;
    }

    public void setFields(List<FieldDef> fields)
    {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }
}
