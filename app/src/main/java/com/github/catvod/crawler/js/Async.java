package com.github.catvod.crawler.js;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java.util.concurrent.CountDownLatch;

public class Async {

    private final Result future;

    private final JSCallFunction success = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            future.complete(args != null && args.length > 0 ? args[0] : null);
            return null;
        }
    };

    private final JSCallFunction error = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            String msg = args != null && args.length > 0 && args[0] != null ? args[0].toString() : "";
            future.completeExceptionally(new Exception(msg));
            return null;
        }
    };

    public static Result run(JSObject object, String name, Object[] args) {
        return new Async().call(object, name, args);
    }

    private Async() {
        this.future = new Result();
    }

    private Result call(JSObject object, String name, Object[] args) {
        JSFunction function = object.getJSFunction(name);
        if (function == null) return empty();
        try {
            Object result = function.call(args);
            if (result instanceof JSObject) then((JSObject) result);
            else future.complete(result);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        } finally {
            function.release();
        }
        return future;
    }

    private Result empty() {
        future.complete(null);
        return future;
    }

    private void then(JSObject promise) {
        JSFunction then = promise.getJSFunction("then");
        if (then == null) {
            future.complete(promise);
        } else {
            consume(then, success);
            consume(promise.getJSFunction("catch"), error);
        }
    }

    private void consume(JSFunction function, JSCallFunction callback) {
        if (function == null) return;
        try {
            function.call(callback);
        } finally {
            function.release();
        }
    }

    public static class Result {
        private final CountDownLatch latch = new CountDownLatch(1);
        private Object value;
        private Throwable error;

        void complete(Object value) {
            this.value = value;
            latch.countDown();
        }

        void completeExceptionally(Throwable error) {
            this.error = error;
            latch.countDown();
        }

        public Object get() throws Exception {
            latch.await();
            if (error != null) {
                if (error instanceof Exception) throw (Exception) error;
                throw new Exception(error);
            }
            return value;
        }
    }
}
