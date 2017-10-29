package com.speedment.runtime.transaction.internal;

import com.speedment.common.injector.State;
import static com.speedment.common.injector.State.STARTED;
import com.speedment.common.injector.annotation.ExecuteBefore;
import com.speedment.common.injector.annotation.WithState;
import com.speedment.runtime.config.Dbms;
import com.speedment.runtime.core.component.ProjectComponent;
import com.speedment.runtime.core.component.connectionpool.ConnectionPoolComponent;
import com.speedment.runtime.core.component.connectionpool.PoolableConnection;
import com.speedment.runtime.core.db.SqlConsumer;
import com.speedment.runtime.transaction.DataSourceHandler;
import com.speedment.runtime.transaction.TransactionComponent;
import com.speedment.runtime.transaction.TransactionHandler;
import com.speedment.runtime.transaction.exception.TransactionException;
import java.sql.SQLException;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 *
 * @author Per Minborg
 */
public class TransactionComponentImpl implements TransactionComponent {

    private final Map<Class<?>, DataSourceHandler<Object, Object>> dataSourceHandlers;
    private final Map<Thread, Object> txObjects;
    private Dbms singleDbms;

    @ExecuteBefore(STARTED)
    private void setupSingleDbms(@WithState(State.RESOLVED) ProjectComponent projectComponent) {
        final Set<Dbms> dbmses = projectComponent.getProject().dbmses().collect(toSet());
        if (dbmses.size() == 1) {
            singleDbms = dbmses.stream().findAny().get();
        }
    }

    @ExecuteBefore(State.STARTED)
    private void addDbmsDataSourceHandler(ConnectionPoolComponent connectionPoolComponent) {
        final Function<Dbms, PoolableConnection> extractor = dbms -> connectionPoolComponent.getConnection(dbms);
        final Consumer<PoolableConnection> starter = wrapSqlException(c -> c.setAutoCommit(false), "commit connection");
        final Consumer<PoolableConnection> committer = wrapSqlException(PoolableConnection::commit, "commit connection");
        final Consumer<PoolableConnection> rollbacker = wrapSqlException(PoolableConnection::rollback, "rollback connection");
        final Consumer<PoolableConnection> closer = wrapSqlException(c -> {
            c.setAutoCommit(true);
            c.close();
        }, "close connection");
        putDataSourceHandler(Dbms.class, DataSourceHandler.of(extractor, starter, committer, rollbacker, closer));
    }

    public TransactionComponentImpl() {
        this.dataSourceHandlers = new ConcurrentHashMap<>();
        this.txObjects = new ConcurrentHashMap<>();
    }

    @Override
    public TransactionHandler transactionHandler() {
        if (singleDbms == null) {
            throw new IllegalStateException("This project does not contain exactly one Dbms.");
        }
        return transactionHandler(singleDbms);
    }

    @Override
    public <T> TransactionHandler transactionHandler(T dataSource) {
        return new TransactionHandlerImpl(this, dataSource, findMapping(dataSource));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <D, T> void putDataSourceHandler(Class<D> dataSourceClass, DataSourceHandler<D, T> dataSourceHandler) {
        dataSourceHandlers.put(dataSourceClass, (DataSourceHandler<Object, Object>) dataSourceHandler);
    }

    @Override
    public void put(Thread thread, Object txObject) {
        if (txObjects.putIfAbsent(thread, txObject) != null) {
            throw new IllegalStateException(
                String.format("There is already a txObject associated with thread %s ", thread)
            );
        };
    }

    @Override
    public Optional<Object> get(Thread thread) {
        return Optional.ofNullable(txObjects.get(requireNonNull(thread)));
    }

    @Override
    public void remove(Thread thread) {
        txObjects.remove(requireNonNull(thread));
    }

    private DataSourceHandler<Object, Object> findMapping(Object dataSource) {
        final Class<?> originalClass = dataSource.getClass();
        {
            final DataSourceHandler<Object, Object> dataSourceHandler = dataSourceHandlers.get(originalClass);
            if (dataSourceHandler != null) {
                return dataSourceHandler;
            }
        }
        for (Class<?> interf : originalClass.getInterfaces()) {
            final DataSourceHandler<Object, Object> dataSourceHandler = dataSourceHandlers.get(interf);
            if (dataSourceHandler != null) {
                dataSourceHandlers.put(interf, dataSourceHandler); // Enter this association to speed up next look-up
                return dataSourceHandler;
            }
        }
        Class<?> superClass = originalClass.getSuperclass();
        while (superClass != null) {
            final DataSourceHandler<Object, Object> dataSourceHandler = dataSourceHandlers.get(superClass);
            if (dataSourceHandler != null) {
                dataSourceHandlers.put(superClass, dataSourceHandler); // Enter this association to speed up next look-up
                return dataSourceHandler;
            }
            superClass = superClass.getSuperclass();
        }
        throw new IllegalArgumentException(
            String.format(
                "Unable to find a mapping for the data source %s of class %s. Available class mappings: ",
                dataSource,
                originalClass,
                dataSourceHandlers.keySet().stream().map(Object::toString).collect(joining(", "))
            )
        );
    }

    private <T> Consumer<T> wrapSqlException(SqlConsumer<T> sqlConsumer, String label) {
        return (T t) -> {
            try {
                sqlConsumer.accept(t);
            } catch (SQLException sqle) {
                throw new TransactionException("Unable to " + label + ": " + t, sqle);
            }
        };
    }

}
