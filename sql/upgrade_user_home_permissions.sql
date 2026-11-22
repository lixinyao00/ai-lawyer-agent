-- 普通咨询用户（role 2 common / role 100 lawyer_user）只分配律师助手前台菜单。
-- 前台集合 FRONT = 2000..2006 + 2011..2020（17 个），后台 ADMIN 集合不在此脚本授予。
-- 可重复执行。
START TRANSACTION;

DELETE grants
FROM sys_role_menu AS grants
JOIN sys_role AS role_definition ON role_definition.role_id = grants.role_id
WHERE role_definition.role_id IN (2, 100)
  AND role_definition.del_flag = '0'
  AND grants.menu_id NOT IN
      (2000, 2001, 2002, 2003, 2004, 2005, 2006,
       2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_definition.role_id, menu.menu_id
FROM sys_role AS role_definition
JOIN sys_menu AS menu ON menu.menu_id IN
    (2000, 2001, 2002, 2003, 2004, 2005, 2006,
     2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020)
WHERE role_definition.role_id IN (2, 100)
  AND role_definition.del_flag = '0';

COMMIT;
