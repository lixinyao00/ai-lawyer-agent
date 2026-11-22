package com.ruoyi.lawyer.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 文书模板字段定义
 *
 * <p>type 取值：text / textarea / date / number / money / select / phone / idcard。</p>
 *
 * @author ruoyi
 */
public class FieldDef implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 字段 key，作为 formData 的键 */
    private String key;

    /** 字段中文名，用于表单标签与校验提示 */
    private String label;

    /** 控件类型：text / textarea / date / number / money / select / phone / idcard */
    private String type;

    /** 是否必填 */
    private Boolean required = Boolean.FALSE;

    /** 输入提示 */
    private String placeholder;

    /** 最大长度（text 默认 200，textarea 默认 2000） */
    private Integer maxLength;

    /** select 类型的可选值 */
    private List<String> options = new ArrayList<>();

    /** 默认值 */
    private Object defaultValue;

    /** 前端分组标题 */
    private String group;

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Boolean getRequired()
    {
        return required;
    }

    public void setRequired(Boolean required)
    {
        this.required = required == null ? Boolean.FALSE : required;
    }

    public String getPlaceholder()
    {
        return placeholder;
    }

    public void setPlaceholder(String placeholder)
    {
        this.placeholder = placeholder;
    }

    public Integer getMaxLength()
    {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength)
    {
        this.maxLength = maxLength;
    }

    public List<String> getOptions()
    {
        return options;
    }

    public void setOptions(List<String> options)
    {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public Object getDefaultValue()
    {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    public String getGroup()
    {
        return group;
    }

    public void setGroup(String group)
    {
        this.group = group;
    }
}
