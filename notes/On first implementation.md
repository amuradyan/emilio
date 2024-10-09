## flow

One way of asking Emilio a question, assuming a dedicated thread, would need to:

* [post a message to a thread](https://platform.openai.com/docs/api-reference/messages/createMessage)
* [run the thread](https://platform.openai.com/docs/api-reference/runs/createRun) and look for a
* message with a specific event type/`thread.message.completed`/ in the response stream.


