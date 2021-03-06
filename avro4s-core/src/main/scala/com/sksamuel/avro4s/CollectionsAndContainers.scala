package com.sksamuel.avro4s

import java.util

import com.sksamuel.avro4s.CollectionsAndContainers._

import org.apache.avro.{Schema, SchemaBuilder}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

trait CollectionAndContainerSchemaFors {

  implicit val noneSchemaFor: SchemaFor[None.type] = CollectionsAndContainers.noneSchemaFor

  implicit def optionSchemaFor[T](implicit value: SchemaFor[T]): SchemaFor[Option[T]] =
    new ResolvableSchemaFor[Option[T]] {
      def schemaFor(env: DefinitionEnvironment[SchemaFor], update: SchemaUpdate): SchemaFor[Option[T]] =
        buildOptionSchemaFor(value.resolveSchemaFor(env, update))
    }

  implicit def eitherSchemaFor[A, B](implicit left: SchemaFor[A], right: SchemaFor[B]): SchemaFor[Either[A, B]] =
    new ResolvableSchemaFor[Either[A, B]] {
      def schemaFor(env: DefinitionEnvironment[SchemaFor], update: SchemaUpdate): SchemaFor[Either[A, B]] =
        buildEitherSchemaFor(left.resolveSchemaFor(env, update), right.resolveSchemaFor(env, update))
    }

  private def _iterableSchemaFor[C[X] <: Iterable[X], T](implicit item: SchemaFor[T]): SchemaFor[C[T]] =
    new ResolvableSchemaFor[C[T]] {
      def schemaFor(env: DefinitionEnvironment[SchemaFor], update: SchemaUpdate): SchemaFor[C[T]] =
        buildIterableSchemaFor(item.resolveSchemaFor(env, update))
    }

  implicit def arraySchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[Array[T]] =
    new ResolvableSchemaFor[Array[T]] {
      def schemaFor(env: DefinitionEnvironment[SchemaFor], update: SchemaUpdate): SchemaFor[Array[T]] =
        item.resolveSchemaFor(env, update).map(SchemaBuilder.array.items(_))
    }

  implicit def iterableSchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[Iterable[T]] =
    _iterableSchemaFor[Iterable, T](item)

  implicit def listSchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[List[T]] =
    _iterableSchemaFor[List, T](item)

  implicit def setSchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[Set[T]] =
    _iterableSchemaFor[Set, T](item)

  implicit def vectorSchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[Vector[T]] =
    _iterableSchemaFor[Vector, T](item)

  implicit def seqSchemaFor[T](implicit item: SchemaFor[T]): SchemaFor[Seq[T]] =
    _iterableSchemaFor[Seq, T](item)

  implicit def mapSchemaFor[T](implicit value: SchemaFor[T]): SchemaFor[Map[String, T]] =
    new ResolvableSchemaFor[Map[String, T]] {
      def schemaFor(env: DefinitionEnvironment[SchemaFor], update: SchemaUpdate): SchemaFor[Map[String, T]] =
        buildMapSchemaFor(value.resolveSchemaFor(env, update))
    }
}

trait CollectionAndContainerEncoders {

  import EncoderHelpers._

  implicit val NoneEncoder: Encoder[None.type] = new Encoder[None.type] {
    val schemaFor: SchemaFor[None.type] = noneSchemaFor
    def encode(value: None.type): AnyRef = null
  }

  implicit def optionEncoder[T](implicit value: Encoder[T]): Encoder[Option[T]] = new ResolvableEncoder[Option[T]] {
    def encoder(env: DefinitionEnvironment[Encoder], update: SchemaUpdate): Encoder[Option[T]] = {
      val encoder = value.resolveEncoder(env, mapFullUpdate(extractOptionSchema, update))

      new Encoder[Option[T]] {

        val schemaFor: SchemaFor[Option[T]] = buildOptionSchemaFor(encoder.schemaFor)

        def encode(value: Option[T]): AnyRef = if (value.isEmpty) null else encoder.encode(value.get)

        override def withSchema(schemaFor: SchemaFor[Option[T]]): Encoder[Option[T]] =
          buildWithSchema(optionEncoder(value), schemaFor)
      }
    }
  }

  implicit def eitherEncoder[A, B](implicit left: Encoder[A], right: Encoder[B]): Encoder[Either[A, B]] =
    new ResolvableEncoder[Either[A, B]] {
      def encoder(env: DefinitionEnvironment[Encoder], update: SchemaUpdate): Encoder[Either[A, B]] = {
        val leftEncoder = left.resolveEncoder(env, mapFullUpdate(extractEitherLeftSchema, update))
        val rightEncoder = right.resolveEncoder(env, mapFullUpdate(extractEitherRightSchema, update))

        new Encoder[Either[A, B]] {
          val schemaFor: SchemaFor[Either[A, B]] = buildEitherSchemaFor(leftEncoder.schemaFor, rightEncoder.schemaFor)

          def encode(value: Either[A, B]): AnyRef = value match {
            case Left(l)  => leftEncoder.encode(l)
            case Right(r) => rightEncoder.encode(r)
          }

          override def withSchema(schemaFor: SchemaFor[Either[A, B]]): Encoder[Either[A, B]] =
            buildWithSchema(eitherEncoder(left, right), schemaFor)
        }
      }
    }

  implicit def arrayEncoder[T: ClassTag](implicit item: Encoder[T]): Encoder[Array[T]] =
    new ResolvableEncoder[Array[T]] {
      def encoder(env: DefinitionEnvironment[Encoder], update: SchemaUpdate): Encoder[Array[T]] = {
        val encoder = item.resolveEncoder(env, mapFullUpdate(extractIterableElementSchema, update))

        new Encoder[Array[T]] {
          val schemaFor: SchemaFor[Array[T]] = buildIterableSchemaFor(encoder.schemaFor).forType

          def encode(value: Array[T]): AnyRef = value.map(encoder.encode).toList.asJava

          override def withSchema(schemaFor: SchemaFor[Array[T]]): Encoder[Array[T]] =
            buildWithSchema(arrayEncoder(implicitly[ClassTag[T]], item), schemaFor)
        }
      }
    }

  private def iterableEncoder[T, C[X] <: Iterable[X]](item: Encoder[T]): Encoder[C[T]] = new ResolvableEncoder[C[T]] {
    def encoder(env: DefinitionEnvironment[Encoder], update: SchemaUpdate): Encoder[C[T]] = {
      val encoder = item.resolveEncoder(env, mapFullUpdate(extractIterableElementSchema, update))

      new Encoder[C[T]] {
        val schemaFor: SchemaFor[C[T]] = buildIterableSchemaFor(encoder.schemaFor)

        def encode(value: C[T]): AnyRef = value.map(encoder.encode).toList.asJava

        override def withSchema(schemaFor: SchemaFor[C[T]]): Encoder[C[T]] =
          buildWithSchema(iterableEncoder(item), schemaFor)
      }
    }
  }

  implicit def listEncoder[T](implicit encoder: Encoder[T]): Encoder[List[T]] = iterableEncoder(encoder)
  implicit def mutableSeqEncoder[T](implicit encoder: Encoder[T]): Encoder[scala.collection.mutable.Seq[T]] =
    iterableEncoder(encoder)
  implicit def seqEncoder[T](implicit encoder: Encoder[T]): Encoder[Seq[T]] = iterableEncoder(encoder)
  implicit def setEncoder[T](implicit encoder: Encoder[T]): Encoder[Set[T]] = iterableEncoder(encoder)
  implicit def vectorEncoder[T](implicit encoder: Encoder[T]): Encoder[Vector[T]] = iterableEncoder(encoder)

  implicit def mapEncoder[T](implicit value: Encoder[T]): Encoder[Map[String, T]] =
    new ResolvableEncoder[Map[String, T]] {
      def encoder(env: DefinitionEnvironment[Encoder], update: SchemaUpdate): Encoder[Map[String, T]] = {
        val encoder = value.resolveEncoder(env, mapFullUpdate(extractMapValueSchema, update))

        new Encoder[Map[String, T]] {
          val schemaFor: SchemaFor[Map[String, T]] = buildMapSchemaFor(encoder.schemaFor)

          def encode(value: Map[String, T]): AnyRef = {
            val map = new util.HashMap[String, AnyRef]
            value.foreach { case (k, v) => map.put(k, encoder.encode(v)) }
            map
          }

          override def withSchema(schemaFor: SchemaFor[Map[String, T]]): Encoder[Map[String, T]] =
            buildWithSchema(mapEncoder(value), schemaFor)
        }
      }
    }
}

trait CollectionAndContainerDecoders {

  import DecoderHelpers._

  implicit val NoneDecoder: Decoder[None.type] = new Decoder[None.type] {
    val schemaFor: SchemaFor[None.type] = noneSchemaFor
    def decode(value: Any): None.type =
      if (value == null) None else sys.error(s"Value $value is not null, but should be decoded to None")
  }

  implicit def optionDecoder[T](implicit value: Decoder[T]): Decoder[Option[T]] = new ResolvableDecoder[Option[T]] {
    def decoder(env: DefinitionEnvironment[Decoder], update: SchemaUpdate): Decoder[Option[T]] = {
      val decoder = value.resolveDecoder(env, mapFullUpdate(extractOptionSchema, update))

      new Decoder[Option[T]] {

        val schemaFor: SchemaFor[Option[T]] = buildOptionSchemaFor(decoder.schemaFor)

        def decode(value: Any): Option[T] = if (value == null) None else Option(decoder.decode(value))

        override def withSchema(schemaFor: SchemaFor[Option[T]]): Decoder[Option[T]] =
          buildWithSchema(optionDecoder(value), schemaFor)
      }
    }
  }

  implicit def eitherDecoder[A: WeakTypeTag, B: WeakTypeTag](implicit left: Decoder[A],
                                                             right: Decoder[B]): Decoder[Either[A, B]] =
    new ResolvableDecoder[Either[A, B]] {
      def decoder(env: DefinitionEnvironment[Decoder], update: SchemaUpdate): Decoder[Either[A, B]] = {
        val leftDecoder = left.resolveDecoder(env, mapFullUpdate(extractEitherLeftSchema, update))
        val rightDecoder = right.resolveDecoder(env, mapFullUpdate(extractEitherRightSchema, update))

        new Decoder[Either[A, B]] {
          val schemaFor: SchemaFor[Either[A, B]] = buildEitherSchemaFor(leftDecoder.schemaFor, rightDecoder.schemaFor)

          private implicit val leftGuard: PartialFunction[Any, A] = TypeGuardedDecoding.guard(leftDecoder)
          private implicit val rightGuard: PartialFunction[Any, B] = TypeGuardedDecoding.guard(rightDecoder)

          def decode(value: Any): Either[A, B] =
            if (leftGuard.isDefinedAt(value)) {
              Left(leftGuard(value))
            } else if (rightGuard.isDefinedAt(value)) {
              Right(rightGuard(value))
            } else {
              val nameA = leftDecoder.schema.getFullName
              val nameB = rightDecoder.schema.getFullName
              sys.error(s"Could not decode $value into Either[$nameA, $nameB]")
            }

          override def withSchema(schemaFor: SchemaFor[Either[A, B]]): Decoder[Either[A, B]] =
            buildWithSchema(eitherDecoder(weakTypeTag[A], weakTypeTag[B], left, right), schemaFor)
        }
      }
    }

  implicit def arrayDecoder[T: ClassTag](implicit item: Decoder[T]): Decoder[Array[T]] =
    new ResolvableDecoder[Array[T]] {
      def decoder(env: DefinitionEnvironment[Decoder], update: SchemaUpdate): Decoder[Array[T]] = {
        val decoder = item.resolveDecoder(env, mapFullUpdate(extractIterableElementSchema, update))

        new Decoder[Array[T]] {
          val schemaFor: SchemaFor[Array[T]] = buildIterableSchemaFor(decoder.schemaFor).forType

          def decode(value: Any): Array[T] = value match {
            case array: Array[_]               => array.map(decoder.decode)
            case list: java.util.Collection[_] => list.asScala.map(decoder.decode).toArray
            case list: Iterable[_]             => list.map(decoder.decode).toArray
            case other                         => sys.error("Unsupported array " + other)
          }

          override def withSchema(schemaFor: SchemaFor[Array[T]]): Decoder[Array[T]] =
            buildWithSchema(arrayDecoder(implicitly[ClassTag[T]], item), schemaFor)
        }
      }
    }

  private def iterableDecoder[T, C[X] <: Iterable[X]](item: Decoder[T], build: Iterable[T] => C[T]): Decoder[C[T]] =
    new ResolvableDecoder[C[T]] {
      def decoder(env: DefinitionEnvironment[Decoder], update: SchemaUpdate): Decoder[C[T]] = {
        val decoder = item.resolveDecoder(env, mapFullUpdate(extractIterableElementSchema, update))

        new Decoder[C[T]] {
          val schemaFor: SchemaFor[C[T]] = buildIterableSchemaFor(decoder.schemaFor)

          def decode(value: Any): C[T] = value match {
            case list: java.util.Collection[_] => build(list.asScala.map(decoder.decode))
            case list: Iterable[_]             => build(list.map(decoder.decode))
            case array: Array[_]               =>
              // converting array to Seq in order to avoid requiring ClassTag[T] as does arrayDecoder.
              build(array.toSeq.map(decoder.decode))
            case other => sys.error("Unsupported array " + other)
          }

          override def withSchema(schemaFor: SchemaFor[C[T]]): Decoder[C[T]] =
            buildWithSchema(iterableDecoder(item, build), schemaFor)
        }
      }
    }

  implicit def listDecoder[T](implicit decoder: Decoder[T]): Decoder[List[T]] =
    iterableDecoder(decoder, _.toList)
  implicit def mutableSeqDecoder[T](implicit decoder: Decoder[T]): Decoder[scala.collection.mutable.Seq[T]] =
    iterableDecoder(decoder, _.toBuffer)
  implicit def seqDecoder[T](implicit decoder: Decoder[T]): Decoder[Seq[T]] =
    iterableDecoder(decoder, _.toSeq)
  implicit def setDecoder[T](implicit decoder: Decoder[T]): Decoder[Set[T]] =
    iterableDecoder(decoder, _.toSet)
  implicit def vectorDecoder[T](implicit decoder: Decoder[T]): Decoder[Vector[T]] =
    iterableDecoder(decoder, _.toVector)

  implicit def mapDecoder[T](implicit value: Decoder[T]): Decoder[Map[String, T]] =
    new ResolvableDecoder[Map[String, T]] {
      def decoder(env: DefinitionEnvironment[Decoder], update: SchemaUpdate): Decoder[Map[String, T]] = {
        val decoder = value.resolveDecoder(env, mapFullUpdate(extractMapValueSchema, update))

        new Decoder[Map[String, T]] {
          val schemaFor: SchemaFor[Map[String, T]] = buildMapSchemaFor(decoder.schemaFor)

          def decode(value: Any): Map[String, T] = value match {
            case map: java.util.Map[_, _] => map.asScala.toMap.map { case (k, v) => k.toString -> decoder.decode(v) }
          }

          override def withSchema(schemaFor: SchemaFor[Map[String, T]]): Decoder[Map[String, T]] =
            buildWithSchema(mapDecoder(value), schemaFor)
        }
      }
    }
}

object CollectionsAndContainers {

  val noneSchemaFor: SchemaFor[None.type] =
    SchemaFor(SchemaBuilder.builder.nullType)

  private[avro4s] def buildOptionSchemaFor[T](schemaFor: SchemaFor[T]): SchemaFor[Option[T]] =
    schemaFor.map[Option[T]](itemSchema => SchemaHelper.createSafeUnion(itemSchema, SchemaBuilder.builder().nullType()))

  private[avro4s] def extractOptionSchema(schema: Schema): Schema = {
    require(schema.getType == Schema.Type.UNION,
            s"Schema type for option encoders / decoders must be UNION, received ${schema.getType}")

    schema.getTypes.asScala.find(_.getType != Schema.Type.NULL) match {
      case Some(s) => s
      case None    => sys.error(s"Union schema $schema doesn't contain any non-null entries")
    }
  }

  private[avro4s] def buildEitherSchemaFor[A, B](leftSchemaFor: SchemaFor[A],
                                                 rightSchemaFor: SchemaFor[B]): SchemaFor[Either[A, B]] =
    SchemaFor(SchemaHelper.createSafeUnion(leftSchemaFor.schema, rightSchemaFor.schema), leftSchemaFor.fieldMapper)

  private[avro4s] def extractEitherLeftSchema(schema: Schema): Schema = {
    validateEitherSchema(schema)
    schema.getTypes.get(0)
  }

  private[avro4s] def extractEitherRightSchema(schema: Schema): Schema = {
    validateEitherSchema(schema)
    schema.getTypes.get(1)
  }

  private[avro4s] def validateEitherSchema(schema: Schema): Unit = {
    require(schema.getType == Schema.Type.UNION,
            s"Schema type for either encoders / decoders must be UNION, received ${schema.getType}")
    require(schema.getTypes.size() == 2,
            s"Schema for either encoders / decoders must be a UNION of to types, received $schema")
  }

  private[avro4s] def buildIterableSchemaFor[C[X] <: Iterable[X], T](schemaFor: SchemaFor[T]): SchemaFor[C[T]] =
    schemaFor.map(SchemaBuilder.array.items(_))

  private[avro4s] def extractIterableElementSchema(schema: Schema): Schema = {
    require(
      schema.getType == Schema.Type.ARRAY,
      s"Schema type for array / list / seq / vector encoders and decoders must be ARRAY, received ${schema.getType}"
    )
    schema.getElementType
  }

  private[avro4s] def buildMapSchemaFor[T](schemaFor: SchemaFor[T]): SchemaFor[Map[String, T]] =
    schemaFor.map(SchemaBuilder.map().values(_))

  private[avro4s] def extractMapValueSchema(schema: Schema): Schema = {
    require(schema.getType == Schema.Type.MAP,
            s"Schema type for map encoders / decoders must be MAP, received ${schema.getType}")
    schema.getValueType
  }
}
