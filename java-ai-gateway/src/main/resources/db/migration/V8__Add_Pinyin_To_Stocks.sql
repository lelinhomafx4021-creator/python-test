-- 添加拼音首字母字段用于拼音搜索
ALTER TABLE stocks ADD COLUMN pinyin VARCHAR(64) DEFAULT NULL COMMENT '拼音首字母（如 gzmt = 贵州茅台）' AFTER name;

-- 为已有股票填充拼音首字母
UPDATE stocks SET pinyin = CASE
  WHEN symbol = '600519' THEN 'gzmt'
  WHEN symbol = '000001' THEN 'payh'
  WHEN symbol = '300750' THEN 'ndsd'
  WHEN symbol = '600036' THEN 'zsyh'
  WHEN symbol = '601318' THEN 'zgpa'
  WHEN symbol = '000858' THEN 'wly'
  WHEN symbol = '002594' THEN 'byd'
  WHEN symbol = '601688' THEN 'zqtz'
  WHEN symbol = '600000' THEN 'pfyh'
  WHEN symbol = '600030' THEN 'zxtz'
  WHEN symbol = '601398' THEN 'gsyh'
  WHEN symbol = '600887' THEN 'ylf'
  WHEN symbol = '000333' THEN 'mjd'
  WHEN symbol = '002714' THEN 'mypy'
  WHEN symbol = '601012' THEN 'lngy'
  WHEN symbol = '300059' THEN 'djcf'
  WHEN symbol = '600276' THEN 'hrgf'
  WHEN symbol = '600309' THEN 'whly'
  WHEN symbol = '002475' THEN 'lzjd'
  WHEN symbol = '000568' THEN 'lzgj'
  ELSE NULL
END
WHERE symbol IN ('600519','000001','300750','600036','601318','000858','002594','601688','600000','600030','601398','600887','000333','002714','601012','300059','600276','600309','002475','000568');

-- 创建索引加速拼音搜索
ALTER TABLE stocks ADD INDEX idx_stocks_pinyin (pinyin);
