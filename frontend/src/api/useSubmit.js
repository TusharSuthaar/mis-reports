import { useState } from 'react';
import { errorText } from './client';
/** Runs a form action, tracking "busy" and turning failures into a message. Resolves true on success. */
export function useSubmit() {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    async function run(action) {
        setBusy(true);
        setError(null);
        try {
            await action();
            return true;
        }
        catch (e) {
            setError(errorText(e));
            return false;
        }
        finally {
            setBusy(false);
        }
    }
    return { busy, error, run };
}
