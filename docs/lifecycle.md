# Engine & Task Lifecycle

ChronoTask maintains a strict, thread-safe lifecycle state machine for the engine and individual tasks.

## Engine Lifecycle

```text
       [start()]               [pause()]
CREATED ───────► RUNNING ◄───────────────────► PAUSED
                   │           [resume()]
                   │
                   ▼ [stop()]
                STOPPED (Terminal)
```

- **`CREATED`**: Tasks can be registered; scheduling loops are not yet launched.
- **`RUNNING`**: Active schedule loops generate triggers and dispatch executions.
- **`PAUSED`**: All automatic trigger generation is paused. Running executions complete normally.
- **`STOPPED`**: Terminal state. All schedulers and pending executions are cancelled and resources are released.

## Task Controls via `TaskHandle`

- `handle.pause()`: Pauses automatic trigger generation for this specific task.
- `handle.resume()`: Resumes automatic trigger generation.
- `handle.triggerNow()`: Immediately dispatches an execution respecting concurrency policies.
- `handle.cancel()`: Cancels task scheduling and stops running instances.
- `handle.unregister()`: Removes that specific registration and cancels its executions. A newer registration using the same ID is preserved.

For fixed-delay tasks, pausing leaves the current execution in progress. Resuming waits
for that execution to complete before measuring the next delay. Cancelling a task
discards its pending queue before cancelling its running executions. Once cancellation
returns, an execution submission still being prepared is rejected.

After unregistering a task, its ID can be reused for a new registration. Late callbacks
from the old registration update only its own runtime and do not affect the new task.
Concurrent unregistration cancels only executions belonging to the old registration,
including when a new task with the same ID has already started.

If registration is still validating when `stop()` transitions the engine to `STOPPED`,
the registration is rejected before it can add the task to the registry.

Scheduler cancellation runs after its generation has been detached and outside the task
lock. A cancellation callback can pause or resume a task without an older lifecycle
operation overwriting the resulting scheduler. Engine pause also stops cancelling
schedulers when a newer resume has returned the engine to `RUNNING`.

Annotation registration batches roll back in reverse order through each original handle.
Custom `TaskHandle` implementations must implement `unregister()` to support this
rollback operation. Its default implementation throws `UnsupportedOperationException`;
rollback records that exception and continues, rather than deleting a task by ID.

Exceptions from trigger evaluation, clock reads, timer waits, or submission stop that task's schedule and appear in `snapshot().scheduleFailure`; `scheduleCompleted` becomes true and `nextScheduledAt` is cleared. Coroutine cancellation from a pause, task cancellation, or a collaborator does not mark the schedule failed. Errors from a superseded scheduler generation cannot overwrite the current generation's state.

Execution launch failures release the reserved concurrency slot. Accepted queued executions that cannot launch report failure and are drained without blocking later submissions. A recovered dispatcher can serve subsequent manual triggers. Annotation rollback also completes remaining cleanup when a custom handle rethrows the original registration exception; that exception is not added as its own suppressed exception.
