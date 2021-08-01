package io.beatmaps.common.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.IsNullOp
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.Instant

fun incrementBy(column: Column<Int>, num: Int = 1) = object : Expression<Int>() {
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
            if (idx > 0) append(" || '$separator' || ")
            append(it)
        }
        append(")")
    }
}

class InsensitiveLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")
infix fun <T : String?> ExpressionWithColumnType<T>.ilike(pattern: String): Op<Boolean> = InsensitiveLikeOp(this, QueryParameter(pattern, columnType))

fun <T : Any> isFalse(query: Op<T>) = object : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        append(query)
        append(") IS FALSE")
    }
}

fun <T : Any> wrapAsExpressionNotNull(query: Query) = object : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        query.prepareSQL(this)
        append(")")
    }
}

fun countWithFilter(condition: Expression<Boolean>): Expression<Int> = object : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +"COUNT(*) FILTER (WHERE "
        +condition
        +")"
    }
}

fun <T> Expression<T>.countWithFilter(condition: Expression<Boolean>): Expression<Int> = object : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +"COUNT("
        +this@countWithFilter
        +") FILTER (WHERE "
        +condition
        +")"
    }
}

operator fun Expression<*>.minus(other: Expression<Instant?>) = object : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +"DATE_PART('days', "
        +this@minus
        +" - "
        +other
        +")"
    }
}
