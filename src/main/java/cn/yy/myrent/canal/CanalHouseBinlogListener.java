package cn.yy.myrent.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Minimal Canal client: subscribes to rent.house binlog and logs row changes.
 */
@Component
@Slf4j
public class CanalHouseBinlogListener implements DisposableBean {

    @Value("${canal.server.host:127.0.0.1}")
    private String host;

    @Value("${canal.server.port:11111}")
    private int port;

    @Value("${canal.server.destination:example}")
    private String destination;

    @Value("${canal.server.username:}")
    private String username;

    @Value("${canal.server.password:}")
    private String password;

    @Value("${canal.server.batchSize:1000}")
    private int batchSize;

    private CanalConnector connector;
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port),
                destination,
                username,
                password
        );
        Thread t = new Thread(this::listen, "canal-house-listener");
        t.setDaemon(true);
        t.start();
        log.info("Canal listener started. host={}, port={}, destination={}", host, port, destination);
    }

    private void listen() {
        try {
            connector.connect();
            connector.subscribe("rent.house");
            connector.rollback();
            while (running) {
                Message message = connector.getWithoutAck(batchSize);
                long batchId = message.getId();
                if (batchId == -1 || message.getEntries().isEmpty()) {
                    Thread.sleep(500);
                    continue;
                }
                try {
                    handleEntries(message.getEntries());
                    connector.ack(batchId);
                } catch (Exception e) {
                    log.error("Canal handle error, rollback batchId={}", batchId, e);
                    connector.rollback(batchId);
                }
            }
        } catch (Exception e) {
            log.error("Canal listener failed to start", e);
        }
    }

    private void handleEntries(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            CanalEntry.EventType eventType = rowChange.getEventType();
            if (!"rent".equalsIgnoreCase(entry.getHeader().getSchemaName()) ||
                    !"house".equalsIgnoreCase(entry.getHeader().getTableName())) {
                continue;
            }
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                String before = formatColumns(rowData.getBeforeColumnsList());
                String after = formatColumns(rowData.getAfterColumnsList());
                log.info("op={} before=[{}] after=[{}]", eventType, before, after);
            }
        }
    }

    private String formatColumns(List<CanalEntry.Column> columns) {
        StringBuilder sb = new StringBuilder();
        for (CanalEntry.Column col : columns) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(col.getName()).append("=").append(col.getValue());
        }
        return sb.toString();
    }

    @Override
    public void destroy() {
        running = false;
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
