ALTER TABLE `message`
  ADD COLUMN `client_message_id` varchar(80) DEFAULT NULL AFTER `receiver_id`,
  ADD UNIQUE KEY `uk_message_sender_client` (`sender_id`, `client_message_id`);
