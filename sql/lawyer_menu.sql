-- ----------------------------
-- AI 律师助手 菜单与权限
-- 可重复执行：先清理 2000-2006 段，再插入
-- 权限点与 LawSessionController / LawChatController 的 @PreAuthorize 一一对应
-- ----------------------------

delete from sys_role_menu where menu_id between 2000 and 2006;
delete from sys_menu where menu_id between 2000 and 2006;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values
(2000, 'AI律师助手', 0,    5, 'lawyer', null,                '', 'Lawyer',     1, 0, 'M', '0', '0', '',                    'clipboard', 'admin', sysdate(), '', null, 'AI律师助手目录'),
(2001, '智能咨询',   2000, 1, 'chat',   'lawyer/chat/index', '', 'LawyerChat', 1, 0, 'C', '0', '0', 'lawyer:session:list',   'message',   'admin', sysdate(), '', null, '智能咨询菜单'),
(2002, '会话新增',   2001, 1, '',       null,                '', '',           1, 0, 'F', '0', '0', 'lawyer:session:add',    '#',         'admin', sysdate(), '', null, ''),
(2003, '会话查询',   2001, 2, '',       null,                '', '',           1, 0, 'F', '0', '0', 'lawyer:session:list',   '#',         'admin', sysdate(), '', null, ''),
(2004, '会话删除',   2001, 3, '',       null,                '', '',           1, 0, 'F', '0', '0', 'lawyer:session:remove', '#',         'admin', sysdate(), '', null, ''),
(2005, '消息查询',   2001, 4, '',       null,                '', '',           1, 0, 'F', '0', '0', 'lawyer:chat:query',     '#',         'admin', sysdate(), '', null, ''),
(2006, '消息发送',   2001, 5, '',       null,                '', '',           1, 0, 'F', '0', '0', 'lawyer:chat:send',      '#',         'admin', sysdate(), '', null, '');

-- 超级管理员和普通角色分配咨询菜单与操作权限
insert into sys_role_menu (role_id, menu_id) values
(1, 2000), (1, 2001), (1, 2002), (1, 2003), (1, 2004), (1, 2005), (1, 2006),
(2, 2000), (2, 2001), (2, 2002), (2, 2003), (2, 2004), (2, 2005), (2, 2006);
