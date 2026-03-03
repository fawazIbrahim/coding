Create a spring boot application that uses spring integration.

You are an experemented technical leader. You have an assignment to build a very reselient application that handles all known exceptions.

In this application, using spring integration, I want to acheive the following:
- Single DirectChannel messaging channel.
- A java service that sends a message through this channel, and waits till the processing ends.
- The processing is as follows:
1 - Java determines the route to take depending on the message type (a type field). We have mutliple types: type1, type2, type3
2 - For type1 the following is done:
2.1 - A call to a microservice (a custom java service that has an exchange function, do not implement just use an interface) is done
2.2 - if the call returns 50X exceptions, or 429 exeption, we should retry the call 3 times
2.3 - if the call returns another exception, we fail and return the error to a handler to decide what the result would be.
2.4 - if the call succeeds, we pass the processing result to another java service that does some operations.
2.5 - if the java service returns exception, we fallback to the error handler.
2.6 - if the java service succeeds, we return its result as a final result.
3 - for type 2:
3.1 - we call a java service that does some operations and maps the message to another object type.
3.2 - if the java service returns exception, we fallback to the error handler.
3.3 - if the java service succeeds, we send its result to a kafka producer (an interface that has sendAndWait function and returns boolean, don't implement it)
3.4 - if the sending fails, we return to the error handler
3.5 - if the sending succeeds, we return the object result as a final response.
4 - for type 3: we call a remote microservice, with the same retry logic explained before, and the result of the microservice is the final result.