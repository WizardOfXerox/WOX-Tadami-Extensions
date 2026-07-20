# Outstanding Bugs

Here is the list of bugs that we haven't fixed yet in our extensions:

## 1. Pornhub Extension
* **Bug**: Searching in the Pornhub extension crashes the app with the following exception:
  ```
  NullPointerException: Attempt to Invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference
  ```
* **Context**: This occurs when initiating a search (typing a query in the search bar and executing it). We have simplified the parsing logic and wrapped the request builder in try-catch blocks, but the NullPointerException persists on Pornhub search execution.
