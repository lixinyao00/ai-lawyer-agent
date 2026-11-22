import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'

/**
 * 鉴权二进制文件下载工具（契约见 PLAN.md §6.4）
 * @param {string} url 接口相对路径
 * @param {string} filename 保存文件名
 * @returns {Promise<void>}
 */
export async function downloadBlob(url, filename) {
  const blob = await request({
    url,
    method: 'get',
    responseType: 'blob'
  })

  if (blob instanceof Blob && blob.type && blob.type.toLowerCase().includes('application/json')) {
    let msg = '下载失败'
    let code
    try {
      const text = await blob.text()
      const parsed = JSON.parse(text)
      if (parsed && parsed.msg) {
        msg = parsed.msg
      }
      if (parsed && parsed.code != null) {
        code = parsed.code
      }
    } catch (_) {
      // ignore json parse error and keep default message
    }
    ElMessage.error(msg || '下载失败')
    const err = new Error(msg || '下载失败')
    if (code != null) {
      err.code = code
    }
    throw err
  }

  const targetBlob = blob instanceof Blob ? blob : new Blob([blob])
  saveAs(targetBlob, filename)
}
