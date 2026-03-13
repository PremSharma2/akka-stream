package part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {


  // ActorSystem is the foundational runtime for Akka.
  // Akka Streams runs on top of Akka Actors, so a stream needs an ActorSystem.
  // You can think of this as the "engine" in which the stream will execute.
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")

  // MATERIALIZER
  //
  // In Akka Streams, a Source, Flow, and Sink together form a STREAM BLUEPRINT.
  // They only describe HOW data should move through the system, but they do not
  // actually process any data yet.
  //
  // Think of it like an architectural drawing of a building.
  //
  // Example:
  //
  // Source ----> Flow ----> Sink
  //
  // This is only a DESIGN of the stream pipeline, not the running system.
  //
  // Nothing executes until the stream is "materialized".
  //
  // -------------------------------------------------------------------
  //
  // WHAT IS MATERIALIZATION?
  //
  // Materialization is the process where Akka converts the stream blueprint
  // (Source, Flow, Sink) into a set of RUNNING actors that actually move data.
  //
  // Internally Akka Streams works on top of the Actor model.
  //
  // During materialization:
  //
  // 1. Actors are created for each stage of the stream
  // 2. These actors get connected together
  // 3. Backpressure channels are established
  // 4. Buffers are allocated
  // 5. The stream execution begins
  //
  // -------------------------------------------------------------------
  //
  // STREAM LIFECYCLE
  //
  // Step 1 — Stream Blueprint
  //
  // val source = Source(1 to 10)
  // val flow   = Flow[Int].map(_ + 1)
  // val sink   = Sink.foreach(println)
  //
  // At this point:
  //
  //   Source --> Flow --> Sink
  //
  // This is only a STRUCTURE, nothing runs yet.
  //
  // -------------------------------------------------------------------
  //
  // Step 2 — Graph Creation
  //
  // val graph = source.via(flow).to(sink)
  //
  // Still nothing is executing.
  //
  // You only built a RunnableGraph (a description of the pipeline).
  //
  // -------------------------------------------------------------------
  //
  // Step 3 — Materialization
  //
  // graph.run()
  //
  // Now the materializer:
  //
  // • Builds actors for the stream stages
  // • Wires them together
  // • Allocates buffers
  // • Starts message passing between stages
  //
  // Now elements actually begin flowing.
  //
  // -------------------------------------------------------------------
  //
  // INTERNAL ACTOR MODEL
  //
  // When materialization happens, Akka internally creates actors like:
  //
  // SourceActor ---> FlowActor ---> SinkActor
  //
  // Data moves as messages between these actors.
  //
  // Example:
  //
  // SourceActor emits: 1
  // FlowActor receives: 1
  // FlowActor emits: 2
  // SinkActor receives: 2
  //
  // -------------------------------------------------------------------
  //
  // BACKPRESSURE SETUP
  //
  // During materialization Akka also sets up backpressure signals.
  //
  // This means downstream components control how fast upstream produces data.
  //
  // Example:
  //
  // Sink is slow
  // ↓
  // Flow slows down
  // ↓
  // Source stops producing temporarily
  //
  // This prevents memory overflow.
  //
  // -------------------------------------------------------------------
  //
  // WHY MATERIALIZER EXISTS
  //
  // The materializer is the component responsible for:
  //
  // • creating the stream actors
  // • wiring the stages together
  // • handling execution
  // • managing buffers
  // • coordinating backpressure
  //
  // Without a materializer the stream cannot start.
  //
  // -------------------------------------------------------------------
  //
  // AKKA VERSION NOTE
  //
  // In Akka versions before 2.6:
  //
  // implicit val materializer = ActorMaterializer()
  //
  // was required.
  //
  // In newer Akka versions:
  //
  // implicit val materializer = SystemMaterializer(system).materializer
  //
  // or it is automatically available.
  //
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // ----------------------------------------------------------
  // SOURCE
  // ----------------------------------------------------------

  // Source is the starting point of a stream.
  // Here, Source(1 to 10) means:
  // "create a stream which emits numbers from 1 to 10, one by one"
  //
  // Type meaning:
  // Source[Int, NotUsed]
  // - Int      = type of elements flowing through the stream
  // - NotUsed  = materialized value of this Source
  //
  // Materialized value means:
  // when the stream starts running, Akka may give back some useful runtime value.
  // In this case the source does not produce any special runtime value, so it is NotUsed.
  val source: Source[Int, NotUsed] = Source(1 to 10)

  // ----------------------------------------------------------
  // SINK
  // ----------------------------------------------------------

  // Sink is the end point of a stream.
  // It "consumes" elements coming from upstream.
  //
  // Sink.foreach[Int](println) means:
  // for every Int received, print it.
  //
  // Type meaning:
  // Sink[Int, Future[Done]]
  // - Int           = this sink accepts Int elements
  // - Future[Done]  = when the stream finishes, Akka gives back a Future[Done]
  //
  // That Future completes when all elements are processed successfully.
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  // ----------------------------------------------------------
  // CONNECT SOURCE TO SINK
  // ----------------------------------------------------------

  // source.to(sink) creates a RunnableGraph.
  //
  // Important concept:
  // At this point the stream is STILL NOT RUNNING.
  // We are only building the stream structure, also called a "blueprint".
  //
  // Think of it like designing plumbing:
  // Source = water producer
  // Flow   = pipe with transformation
  // Sink   = place where water ends up
  //
  // Until we call .run(), no data moves.
  val graph = source.to(sink)

  // .run() materializes the stream.
  // This is the moment Akka creates the actual runtime machinery and starts pushing/pulling elements.
  //
  // Since our sink materializes to Future[Done], graph.run() will return that Future[Done].
  graph.run()

  // ----------------------------------------------------------
  // FLOW Transformer
  // ----------------------------------------------------------

  // Flow represents a processing stage in the middle of a stream.
  // It takes input elements, transforms them, and emits output elements.
  //
  // Flow[Int].map(x => x + 1) means:
  // - accept Int values
  // - add 1 to each incoming value
  // - emit the transformed Int downstream
  //
  // Example:
  // input  = 1, 2, 3
  // output = 2, 3, 4
  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)

  // source.via(flow) means:
  // take the elements produced by source,
  // pass them through the flow,
  // and return a NEW source representing the transformed stream.
  //
  // Original source emits: 1,2,3,...10
  // New source emits:      2,3,4,...11
  //
  // via = "send through this processing stage"
  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)

  // This connects the transformed source to the sink,
  // so the incremented values will be printed when run.
  val graphWithFlow = sourceWithFlow.to(sink)

  // Running this graph will print:
  // 2
  // 3
  // 4
  // ...
  // 11
  graphWithFlow.run()

  // ----------------------------------------------------------
  // FLOW TO SINK
  // ----------------------------------------------------------

  // flow.to(sink) is also valid, but notice what it creates:
  //
  // It creates a Sink[Int, Future[Done]]
  //
  // Why?
  // Because:
  // - the flow expects Int input
  // - the sink consumes the output of that flow
  //
  // So together, they form a bigger sink-like structure:
  // "Give me Ints, I will transform them, then print them"
  //
  // This is useful if later you want to plug a Source into this combined component.
  val flowWithSink = flow.to(sink)

  // Now we can attach a source directly to this combined sink.
  // This will again print incremented values from 2 to 11.
  source.to(flowWithSink).run()

  sourceWithFlow.to(sink).run()

  //  source.to(flowWithSink).run()
  // more functional
   source.via(flow).to(sink).run()

  // nulls are NOT allowed
   val illegalSource = Source.single[String](null)
   illegalSource.to(Sink.foreach(println)).run()
 // to avoid this use Options instead

  // various kinds of sources
  // Creates a Source which emits exactly ONE element and then completes.
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1)) // do not confuse an Akka stream with a "collection" Stream
  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(42))


  // Types of Sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)



//  // flows - usually mapped to collection operators
//  val mapFlow = Flow[Int].map(x => 2 * x)
//  val takeFlow = Flow[Int].take(5)
//  // drop, filter
//  // NOT have flatMap
//
//  // source -> flow -> flow -> ... -> sink
//  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
//  //  doubleFlowGraph.run()
//
//  // syntactic sugars
//  val mapSource = Source(1 to 10).map(x => x * 2) // Source(1 to 10).via(Flow[Int].map(x => x * 2))
//  // run streams directly
//  //  mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()
//
//  // OPERATORS = components
//
//  /**
//   * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names with length > 5 characters.
//   *
//   */
//  val names = List("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams")
//  val nameSource = Source(names)
//  val longNameFlow = Flow[String].filter(name => name.length > 5)
//  val limitFlow = Flow[String].take(2)
//  val nameSink = Sink.foreach[String](println)
//
//  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()
//  nameSource.filter(_.length > 5).take(2).runForeach(println)

}