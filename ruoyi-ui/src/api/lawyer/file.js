import request from '@/utils/request'
import { downloadBlob } from '@/utils/lawyer/download'

// 上传私有文件（multipart/form-data）
export function uploadFile(file) {
  let formData
  if (file instanceof FormData) {
    formData = file
  } else {
    formData = new FormData()
    formData.append('file', file)
  }
  return request({
    url: '/lawyer/files/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    timeout: 120000
  })
}

// 鉴权下载私有文件
export function downloadFile(id, filename) {
  return downloadBlob('/lawyer/files/' + id + '/download', filename || ('file-' + id))
}
