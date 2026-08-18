import { HttpErrorResponse } from '@angular/common/http';
import { ErrorCode, ErrorResponse, FieldValidationError } from './api-types';

/**
 * Normalized error shape used everywhere in the UI. The HTTP layer never leaks
 * past the error interceptor — components only ever see one of these.
 */
export interface AppError {
  code: ErrorCode;
  message: string;
  fieldErrors: FieldValidationError[];
  status: number;
}

const FRIENDLY_MESSAGES: Partial<Record<ErrorCode, string>> = {
  NETWORK_ERROR: 'Cannot reach the server. Check that the backend is running.',
  INTERNAL_ERROR: 'Something went wrong on the server. Please try again.',
  CONCURRENT_MODIFICATION: 'That conflicted with another change. Please retry.',
};

export function toAppError(error: unknown): AppError {
  if (!(error instanceof HttpErrorResponse)) {
    return { code: 'INTERNAL_ERROR', message: 'Unexpected client error.', fieldErrors: [], status: 0 };
  }

  // status 0 means the request never reached the server (offline, CORS, backend down)
  if (error.status === 0) {
    return {
      code: 'NETWORK_ERROR',
      message: FRIENDLY_MESSAGES.NETWORK_ERROR!,
      fieldErrors: [],
      status: 0,
    };
  }

  const body = error.error as Partial<ErrorResponse> | null;
  const code = (body?.errorCode ?? 'INTERNAL_ERROR') as ErrorCode;
  return {
    code,
    message: FRIENDLY_MESSAGES[code] ?? body?.message ?? 'Request failed.',
    fieldErrors: body?.fieldErrors ?? [],
    status: error.status,
  };
}

/** Pulls the message for a single field out of a VALIDATION_FAILED response. */
export function fieldError(error: AppError | null, field: string): string | null {
  return error?.fieldErrors.find((candidate) => candidate.field === field)?.message ?? null;
}
