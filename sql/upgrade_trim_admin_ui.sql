-- 保留原有记录并停用与律师平台无关的后台入口及其按钮权限。
START TRANSACTION;

UPDATE sys_menu
SET status = '1'
WHERE status = '0'
  AND (
    component IN (
      'system/menu/index',
      'system/dept/index',
      'system/post/index',
      'system/dict/index',
      'system/config/index',
      'system/notice/index',
      'tool/build/index'
    )
    OR perms LIKE 'system:menu:%'
    OR perms LIKE 'system:dept:%'
    OR perms LIKE 'system:post:%'
    OR perms LIKE 'system:dict:%'
    OR perms LIKE 'system:config:%'
    OR perms LIKE 'system:notice:%'
    OR perms LIKE 'tool:build:%'
    OR (parent_id = 0 AND path IN ('http://ruoyi.vip', 'https://ruoyi.vip'))
  );

-- 如果工具目录还有自定义入口，则继续显示该目录。
UPDATE sys_menu AS tools
LEFT JOIN sys_menu AS child ON child.parent_id = tools.menu_id AND child.status = '0'
SET tools.status = '1'
WHERE tools.parent_id = 0
  AND tools.menu_type = 'M'
  AND tools.path = 'tool'
  AND tools.status = '0'
  AND child.menu_id IS NULL;

COMMIT;
