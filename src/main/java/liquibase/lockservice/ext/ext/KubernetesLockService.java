package liquibase.lockservice.ext.ext;

import hu.gyuuu.liquibasekubernetes.KubernetesConnector;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.LockException;
import liquibase.executor.ExecutorService;
import liquibase.lockservice.StandardLockService;
import liquibase.statement.core.SelectFromDatabaseChangeLogLockStatement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;

import java.util.StringTokenizer;

@Slf4j
public class KubernetesLockService extends StandardLockService {

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public boolean supports(Database database) {
        return KubernetesConnector.getInstance().isConnected();
    }

    @Override
    public void waitForLock() throws LockException {
        try {
            String lockedBy = ExecutorService.getInstance().getExecutor(database).queryForObject(new SelectFromDatabaseChangeLogLockStatement("LOCKEDBY"), String.class);
            log.trace("Database locked by: "+lockedBy);
            StringTokenizer tok = new StringTokenizer(lockedBy,":");
            if(tok.countTokens() == 2){
                String podNamespace = tok.nextToken();
                String podName = tok.nextToken();
                if(KubernetesConnector.getInstance().isCurrentPod(podNamespace, podName)){
                    log.debug("Lock created by the same pod, release lock");
                    releaseLock();
                }
                Boolean lockHolderPodActive = KubernetesConnector.getInstance().isPodActive(podNamespace, podName);
                if(lockHolderPodActive != null && !lockHolderPodActive){
                    log.debug("Lock created by an inactive pod, release lock");
                    releaseLock();
                }
            } else {
                log.debug("Can't parse LOCKEDBY field: "+lockedBy);
            }
        } catch (DatabaseException e) {
            log.error("Can't read the LOCKEDBY field from databasechangeloglock", e);
        }
        super.waitForLock();
    }
}


