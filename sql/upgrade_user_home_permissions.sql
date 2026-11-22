-- 普通角色只分配智能咨询。其他角色保留原有授权。
START TRANSACTION;

DELETE grants
FROM sys_role_menu AS grants
JOIN sys_role AS role_definition ON role_definition.role_id = grants.role_id
WHERE role_definition.role_id = 2
  AND role_definition.role_key = 'common'
  AND grants.menu_id NOT BETWEEN 2000 AND 2006;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_definition.role_id, menu.menu_id
FROM sys_role AS role_definition
JOIN sys_menu AS menu ON menu.menu_id BETWEEN 2000 AND 2006
WHERE role_definition.role_id = 2
  AND role_definition.role_key = 'common';

COMMIT;
