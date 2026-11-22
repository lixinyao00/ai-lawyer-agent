-- 应用于已初始化的数据库。关闭已下线功能的菜单和按钮权限，可重复执行。
UPDATE sys_menu
SET status = '1'
WHERE status = '0'
  AND (
    component IN (
      'monitor/job/index',
      'monitor/druid/index',
      'monitor/server/index',
      'monitor/cache/index',
      'monitor/cache/list',
      'tool/gen/index',
      'tool/swagger/index'
    )
    OR perms LIKE 'monitor:job:%'
    OR perms LIKE 'monitor:druid:%'
    OR perms LIKE 'monitor:server:%'
    OR perms LIKE 'monitor:cache:%'
    OR perms LIKE 'tool:gen:%'
    OR perms LIKE 'tool:swagger:%'
  );
