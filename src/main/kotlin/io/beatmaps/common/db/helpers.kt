package io.beatmaps.common.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.transactions.TransactionManager

fun incrementBy(column: Column<Int>, num: Int = 1) = object: Expression<Int>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("${TransactionManager.current().identity(column)} + $num")
    }
}

class SimilarOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<%")

infix fun ExpressionWithColumnType<String>.similar(t: String?): Op<Boolean> {
    return if (t == null) {
        IsNullOp(this)
    } else {
        SimilarOp(QueryParameter(t, columnType), this)
    }
}

class PgConcat(
    /** Returns the delimiter. */
    val separator: String,
    /** Returns the expressions being concatenated. */
    vararg val expr: Expression<*>
) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("(")
        expr.forEachIndexed { idx, it ->
            if (idx >  0) append(" || '$separator' || ")
            append(it)
        }
        append(")")
    }
}

class InsensitiveLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")
infix fun<T:String?> ExpressionWithColumnType<T>.ilike(pattern: String): Op<Boolean> = InsensitiveLikeOp(this, QueryParameter(pattern, columnType))

fun <T:Any> isFalse(query: Op<T>) = object : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        append(query)
        append(") IS FALSE")
    }
}

fun <T:Any> wrapAsExpressionNotNull(query: Query) = object : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        query.prepareSQL(this)
        append(")")
    }
}