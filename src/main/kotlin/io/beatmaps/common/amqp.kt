package io.beatmaps.common

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import io.ktor.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import pl.jutupe.ktor_rabbitmq.RabbitMQConfiguration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger
import kotlin.reflect.KClass

val es: ExecutorService = Executors.newFixedThreadPool(4)

val hostname: String = System.getenv("HOSTNAME") ?: ""
val rabbitHost: String = System.getenv("RABBITMQ_HOST") ?: ""
val rabbitPort: String = System.getenv("RABBITMQ_PORT") ?: "5672"
val rabbitUser: String = System.getenv("RABBITMQ_USER") ?: "guest"
val rabbitPass: String = System.getenv("RABBITMQ_PASS") ?: "guest"
private val rabbitLogger = Logger.getLogger("bmio.RabbitMQ")

fun RabbitMQConfiguration.setupAMQP() = apply {
    uri = "amqp://$rabbitUser:$rabbitPass@$rabbitHost:$rabbitPort"
    connectionName = hostname

    serialize { jackson.writeValueAsBytes(it) }
    deserialize { bytes, type -> jackson.readValue(bytes, type.javaObjectType) }

    initialize {
        val queueConfig = mapOf("x-dead-letter-exchange" to "beatmaps.dlq")

        // TODO: Move this to individual apps
        exchangeDeclare("beatmaps.dlq", BuiltinExchangeType.TOPIC, true)
        exchangeDeclare("beatmaps", BuiltinExchangeType.TOPIC, true)
        queueDeclare("vote", true, false, false, queueConfig)
        queueBind("vote", "beatmaps", "vote.#")

        queueDeclare("uvstats", true, false, false, queueConfig)
        queueBind("uvstats", "beatmaps", "user.stats.*")

        queueDeclare("bs.update", true, false, false, queueConfig)
        queueBind("bs.update", "beatmaps", "beatsaver.update")

        queueDeclare("bs.download", true, false, false, queueConfig)
        queueBind("bs.download", "beatmaps", "beatsaver.download")

        queueDeclare("bs.new", true, false, false, queueConfig)
        queueBind("bs.new", "beatmaps", "beatsaver.new")

        queueDeclare("bm.updateStream", true, false, false, queueConfig)
        queueBind("bm.updateStream", "beatmaps", "maps.*.updated")
    }
}

fun Application.rabbitOptional(configuration: RabbitMQ.() -> Unit) {
    if (rabbitHost.isNotEmpty()) {
        feature(RabbitMQ).apply(configuration)
    } else {
        rabbitLogger.warning("RabbitMQ not set up")
    }
}

private fun RabbitMQ.getConnection() =
    javaClass.getDeclaredField("connection").let {
        it.isAccessible = true
        it.get(this) as Connection
    }

fun <T : Any> RabbitMQ.consumeAck(
    queue: String,
    clazz: KClass<T>,
    rabbitDeliverCallback: suspend (consumerTag: String, body: T) -> Unit
) {
    val logger = Logger.getLogger("bmio.RabbitMQ.consumeAck")
    GlobalScope.launch(Dispatchers.IO) {
        getConnection().createChannel().apply {
            basicConsume(
                queue,
                false,
                DeliverCallback { consumerTag, message ->
                    runCatching {
                        val mappedEntity = jackson.readValue(message.body, clazz.javaObjectType)

                        runBlocking(es.asCoroutineDispatcher()) {
                            withContext(es.asCoroutineDispatcher()) {
                                rabbitDeliverCallback.invoke(consumerTag, mappedEntity)
                            }
                        }

                        basicAck(message.envelope.deliveryTag, false)
                    }.getOrElse {
                        logger.warning("Or else: ${it.message}")

                        basicNack(message.envelope.deliveryTag, false, false)
                    }
                },
                CancelCallback {
                    logger.warning("Consume cancelled: $it")
                }
            )
        }
    }
}