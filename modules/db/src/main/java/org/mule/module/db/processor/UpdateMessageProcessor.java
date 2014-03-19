/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.processor;

import org.mule.api.MuleEvent;
import org.mule.module.db.domain.autogeneratedkey.AutoGeneratedKeyStrategy;
import org.mule.module.db.domain.autogeneratedkey.NoAutoGeneratedKeyStrategy;
import org.mule.module.db.domain.connection.DbConnection;
import org.mule.module.db.domain.executor.QueryExecutor;
import org.mule.module.db.domain.executor.QueryExecutorFactory;
import org.mule.module.db.domain.query.Query;
import org.mule.module.db.domain.query.QueryType;
import org.mule.module.db.domain.transaction.TransactionalAction;
import org.mule.module.db.resolver.database.DbConfigResolver;
import org.mule.module.db.resolver.query.QueryResolver;

import java.sql.SQLException;
import java.util.List;

/**
 * Executes an update query on a database.
 * <p/>
 * An update query can be an update, insert or delete query, or a stored procedure
 * taking input parameters only and returning an update count.
 * <p/>
 * Both database and queries are resolved, if required, using the {@link MuleEvent}
 * being processed.
 */
public class UpdateMessageProcessor extends AbstractSingleQueryDbMessageProcessor
{

    private AutoGeneratedKeyStrategy autoGeneratedKeyStrategy;
    private final QueryExecutorFactory queryExecutorFactory;
    private final List<QueryType> validQueryTypes;

    public UpdateMessageProcessor(DbConfigResolver dbConfigResolver, QueryResolver queryResolver, QueryExecutorFactory queryExecutorFactory, TransactionalAction transactionalAction, List<QueryType> validQueryTypes)
    {
        super(dbConfigResolver, queryResolver, transactionalAction);
        this.queryExecutorFactory = queryExecutorFactory;
        this.validQueryTypes = validQueryTypes;
        this.autoGeneratedKeyStrategy = new NoAutoGeneratedKeyStrategy();
    }

    @Override
    protected List<QueryType> getValidQueryTypes()
    {
        return validQueryTypes;
    }

    @Override
    protected Object doExecuteQuery(DbConnection connection, Query query) throws SQLException
    {
        QueryExecutor queryExecutor = queryExecutorFactory.create();
        return queryExecutor.execute(connection, query, autoGeneratedKeyStrategy);
    }

    public AutoGeneratedKeyStrategy getAutoGeneratedKeyStrategy()
    {
        return autoGeneratedKeyStrategy;
    }

    public void setAutoGeneratedKeyStrategy(AutoGeneratedKeyStrategy autoGeneratedKeyStrategy)
    {
        this.autoGeneratedKeyStrategy = autoGeneratedKeyStrategy;
    }
}
