CREATE TABLE `payment_refund` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号，系统唯一',
  `request_no` varchar(64) NOT NULL COMMENT '退款请求幂等号，同一业务请求必须唯一',

  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `payment_no` varchar(64) NOT NULL COMMENT '原支付单号',
  `user_id` bigint NOT NULL COMMENT '退款所属用户',
  `channel` varchar(32) NOT NULL COMMENT '退款渠道，通常与支付渠道一致',

  `refund_amount` int NOT NULL COMMENT '退款金额，单位分',
  `source_type` tinyint NOT NULL COMMENT '来源类型：1用户申请 2超时关单后支付成功不可恢复 3重复支付 4后台人工 5其他补偿',
  `reason_code` varchar(64) NOT NULL COMMENT '退款原因编码，如 USER_APPLY、LATE_SUCCESS_UNRECOVERABLE、DUPLICATE_PAID',
  `reason_detail` varchar(255) DEFAULT NULL COMMENT '退款原因说明',

  `status` tinyint NOT NULL DEFAULT '0' COMMENT '退款状态：0待处理 1处理中 2退款成功 3待重试 4退款失败 5待人工处理 6已取消',
  `third_party_trade_no` varchar(64) DEFAULT NULL COMMENT '原第三方支付单号',
  `third_party_refund_no` varchar(64) DEFAULT NULL COMMENT '第三方退款单号',

  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `max_retry_count` int NOT NULL DEFAULT '10' COMMENT '最大重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '最近一次失败原因',

  `apply_time` datetime NOT NULL COMMENT '退款申请时间',
  `success_time` datetime DEFAULT NULL COMMENT '退款成功时间',
  `close_time` datetime DEFAULT NULL COMMENT '退款关闭/终止时间',

  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  UNIQUE KEY `uk_third_party_refund_no` (`third_party_refund_no`),

  KEY `idx_order_no` (`order_no`),
  KEY `idx_payment_no` (`payment_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_next_retry_time` (`status`, `next_retry_time`),
  KEY `idx_source_type_status` (`source_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';
