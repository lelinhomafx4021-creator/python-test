-- ============================================
-- 测试用户批量插入脚本
-- 执行方式: docker exec -i ai-investor-mysql mysql -uroot -p123456 ai_investor < seed_test_users.sql
-- ============================================
-- 密码: 123456 → admin/zhangsan/lisi/wangwu/zhaoliu/investor/trader01
--        test123 → xiaoming/test01/test02

-- admin (已有则跳过)
INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'admin','$2b$12$daotQ/bSyAgvz7tanneEEu/QlPZv32nQpSaW7Ek4rozAB9T9EW/2.','$2b$12$daotQ/bSyAgvz7tanneEEu/QlPZv32nQpSaW7Ek4rozAB9T9EW/2.','13800000000','系统管理员','admin',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='admin');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'zhangsan','$2b$12$BMC8RfoHXy8J8K6igycKMefu4QZdaUfa0kn1Q.bIMVWqAMsNnsEWW','$2b$12$BMC8RfoHXy8J8K6igycKMefu4QZdaUfa0kn1Q.bIMVWqAMsNnsEWW','13811112222','张三','normal',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='zhangsan');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'lisi','$2b$12$EqKy6z/ijHs7uOq3AG1yB.2EfOnkJX4oh6Wgz39/KmGwtZzXoPPmO','$2b$12$EqKy6z/ijHs7uOq3AG1yB.2EfOnkJX4oh6Wgz39/KmGwtZzXoPPmO','13833334444','李四','normal',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='lisi');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'wangwu','$2b$12$xnCHAtlYnCmAQ/LD.XsHte.yBfvFZusOKNzcy4sp/Tdax1bZH.lxO','$2b$12$xnCHAtlYnCmAQ/LD.XsHte.yBfvFZusOKNzcy4sp/Tdax1bZH.lxO','13855556666','王五','normal',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='wangwu');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'zhaoliu','$2b$12$3VZqK/dfx593P.GyM92hxOPUfU8yBLu57z25AaZiTLg6zMk4qAm6C','$2b$12$3VZqK/dfx593P.GyM92hxOPUfU8yBLu57z25AaZiTLg6zMk4qAm6C','13877778888','赵六','vip',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='zhaoliu');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'xiaoming','$2b$12$NnIYPw7PFIrVPdJ1hs1aq.Xkd6YpsQlV9bwdNKfuM9RsRDnW.3J2W','$2b$12$NnIYPw7PFIrVPdJ1hs1aq.Xkd6YpsQlV9bwdNKfuM9RsRDnW.3J2W','13899990000','小明同学','normal',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='xiaoming');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'test01','$2b$12$zi5jhuMtkMnP.4n88SxjTe6VwLZa7ZQ/MErIsL7sUtphEDoTpjipG','$2b$12$zi5jhuMtkMnP.4n88SxjTe6VwLZa7ZQ/MErIsL7sUtphEDoTpjipG','13911112222','测试用户01','guest',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='test01');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'test02','$2b$12$pvkuN1HbAotwT7eHZxaG.uwbG0bs7gaTsfVjCwJOCqy9799pt3KqG','$2b$12$pvkuN1HbAotwT7eHZxaG.uwbG0bs7gaTsfVjCwJOCqy9799pt3KqG','13933334444','测试用户02','guest',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='test02');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'investor','$2b$12$o9f6idihUe84v483Pime7O2N14fuOiO0APx2WFPAWs4wblAV4guiy','$2b$12$o9f6idihUe84v483Pime7O2N14fuOiO0APx2WFPAWs4wblAV4guiy','13955556666','价值投资者','vip',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='investor');

INSERT INTO `users` (`username`,`password_hash`,`password`,`phone`,`nickname`,`role`,`status`,`created_at`,`updated_at`) SELECT 'trader01','$2b$12$Mejj9qrDGAVwBIS3UD5dReNuQKmUnpihvgf/BmtExd9.UQbCLPFDm','$2b$12$Mejj9qrDGAVwBIS3UD5dReNuQKmUnpihvgf/BmtExd9.UQbCLPFDm','13977778888','短线交易员','normal',1,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username`='trader01');
