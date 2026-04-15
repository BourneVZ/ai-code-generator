/**
 * 代码生成类型枚举
 */
export enum CodeGenTypeEnum {
  HTML = 'html',
  MULTI_FILE = 'multi_file',
  VUE_PROJECT = 'vue_project',
}

type CodeGenTypeConfig = {
  label: string
  value: CodeGenTypeEnum
}

/**
 * 代码生成类型配置
 */
export const CODE_GEN_TYPE_CONFIG = {
  [CodeGenTypeEnum.HTML]: {
    label: '单页 HTML',
    value: CodeGenTypeEnum.HTML,
  },
  [CodeGenTypeEnum.MULTI_FILE]: {
    label: '多文件网站',
    value: CodeGenTypeEnum.MULTI_FILE,
  },
  [CodeGenTypeEnum.VUE_PROJECT]: {
    label: 'Vue 项目',
    value: CodeGenTypeEnum.VUE_PROJECT,
  },
} as const satisfies Record<CodeGenTypeEnum, CodeGenTypeConfig>

/**
 * 代码生成类型选项
 */
export const CODE_GEN_TYPE_OPTIONS = Object.values(CODE_GEN_TYPE_CONFIG).map((config) => ({
  label: config.label,
  value: config.value,
}))

export const DEFAULT_CODE_GEN_TYPE = CodeGenTypeEnum.MULTI_FILE

/**
 * 格式化代码生成类型
 */
export const formatCodeGenType = (type: string | undefined): string => {
  if (!type) {
    return '未知类型'
  }

  const config = CODE_GEN_TYPE_CONFIG[type as CodeGenTypeEnum]
  return config ? config.label : type
}

/**
 * 获取所有代码生成类型
 */
export const getAllCodeGenTypes = () => {
  return Object.values(CodeGenTypeEnum)
}

/**
 * 校验代码生成类型是否合法
 */
export const isValidCodeGenType = (type: string): type is CodeGenTypeEnum => {
  return Object.values(CodeGenTypeEnum).includes(type as CodeGenTypeEnum)
}
