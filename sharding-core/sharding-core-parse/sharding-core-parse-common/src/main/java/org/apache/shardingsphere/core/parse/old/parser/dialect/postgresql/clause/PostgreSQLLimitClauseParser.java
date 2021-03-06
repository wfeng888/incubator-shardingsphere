/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.core.parse.old.parser.dialect.postgresql.clause;

import com.google.common.base.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.token.OffsetToken;
import org.apache.shardingsphere.core.parse.antlr.sql.token.RowCountToken;
import org.apache.shardingsphere.core.parse.old.lexer.LexerEngine;
import org.apache.shardingsphere.core.parse.old.lexer.dialect.postgresql.PostgreSQLKeyword;
import org.apache.shardingsphere.core.parse.old.lexer.token.DefaultKeyword;
import org.apache.shardingsphere.core.parse.old.lexer.token.Literals;
import org.apache.shardingsphere.core.parse.old.lexer.token.Symbol;
import org.apache.shardingsphere.core.parse.old.parser.clause.SQLClauseParser;
import org.apache.shardingsphere.core.parse.old.parser.context.limit.Limit;
import org.apache.shardingsphere.core.parse.old.parser.context.limit.LimitValue;
import org.apache.shardingsphere.core.parse.old.parser.exception.SQLParsingException;
import org.apache.shardingsphere.core.util.NumberUtil;

/**
 * Limit clause parser for PostgreSQL.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class PostgreSQLLimitClauseParser implements SQLClauseParser {
    
    private final LexerEngine lexerEngine;
    
    /**
     * Parse limit.
     * 
     * @param selectStatement select statement
     */
    public void parse(final SelectStatement selectStatement) {
        Optional<LimitValue> offset = Optional.absent();
        Optional<LimitValue> rowCount = Optional.absent();
        while (true) {
            if (lexerEngine.skipIfEqual(PostgreSQLKeyword.LIMIT)) {
                rowCount = buildRowCount(selectStatement);
            } else if (lexerEngine.skipIfEqual(PostgreSQLKeyword.OFFSET)) {
                offset = buildOffset(selectStatement);
            } else {
                break;
            }
        }
        if (offset.isPresent() || rowCount.isPresent()) {
            setLimit(offset, rowCount, selectStatement);
        }
    }
    
    private Optional<LimitValue> buildRowCount(final SelectStatement selectStatement) {
        int parameterIndex = selectStatement.getParametersIndex();
        int rowCountValue = -1;
        int rowCountIndex = -1;
        int valueBeginPosition = lexerEngine.getCurrentToken().getEndPosition();
        if (lexerEngine.equalAny(DefaultKeyword.ALL)) {
            lexerEngine.nextToken();
        } else {
            if (lexerEngine.equalAny(Literals.INT, Literals.FLOAT)) {
                rowCountValue = NumberUtil.roundHalfUp(lexerEngine.getCurrentToken().getLiterals());
                valueBeginPosition = valueBeginPosition - (rowCountValue + "").length();
                selectStatement.addSQLToken(new RowCountToken(valueBeginPosition, rowCountValue));
            } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
                rowCountIndex = parameterIndex++;
                selectStatement.setParametersIndex(parameterIndex);
                rowCountValue = -1;
            } else {
                throw new SQLParsingException(lexerEngine);
            }
            lexerEngine.nextToken();
        }
        return Optional.of(new LimitValue(rowCountValue, rowCountIndex, false));
    }
    
    private Optional<LimitValue> buildOffset(final SelectStatement selectStatement) {
        int parameterIndex = selectStatement.getParametersIndex();
        int offsetValue = -1;
        int offsetIndex = -1;
        int offsetBeginPosition = lexerEngine.getCurrentToken().getEndPosition();
        if (lexerEngine.equalAny(Literals.INT, Literals.FLOAT)) {
            offsetValue = NumberUtil.roundHalfUp(lexerEngine.getCurrentToken().getLiterals());
            offsetBeginPosition = offsetBeginPosition - (offsetValue + "").length();
            selectStatement.addSQLToken(new OffsetToken(offsetBeginPosition, offsetValue));
        } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
            offsetIndex = parameterIndex++;
            selectStatement.setParametersIndex(parameterIndex);
        } else {
            throw new SQLParsingException(lexerEngine);
        }
        lexerEngine.nextToken();
        lexerEngine.skipIfEqual(DefaultKeyword.ROW, PostgreSQLKeyword.ROWS);
        return Optional.of(new LimitValue(offsetValue, offsetIndex, true));
    }
    
    private void setLimit(final Optional<LimitValue> offset, final Optional<LimitValue> rowCount, final SelectStatement selectStatement) {
        Limit limit = new Limit();
        if (offset.isPresent()) {
            limit.setOffset(offset.get());
        }
        if (rowCount.isPresent()) {
            limit.setRowCount(rowCount.get());
        }
        selectStatement.setLimit(limit);
    }
}
