import { invokeScreenAction } from './screens'
import {
  buildScreenActionPayload,
  ScreenActionDisabledError,
  type ScreenActionDto,
  type ScreenResponseBase,
} from './screenTypes'

/**
 * Minimal editor-screen action orchestration shared by DeckEditor and PresetEditor.
 * It only handles action lookup, payload patch application, invoke, and optional refresh.
 * Editor-specific effects such as delete navigation or validate snapshot updates stay in each page.
 * Errors intentionally bubble to the page so each editor can surface its own message.
 */

type EditorActionRequestArgs<
  TScreen extends ScreenResponseBase<TAction>,
  TAction extends ScreenActionDto<TPayload>,
  TActionId extends string,
  TState,
  TPayload extends Record<string, unknown>,
> = {
  screen: TScreen
  actionId: TActionId
  editorState: TState
  findAction: (screen: TScreen, actionId: TActionId) => TAction | null
  buildPatch: (actionId: TActionId, editorState: TState) => Partial<TPayload> | null
}

export function prepareEditorScreenActionRequest<
  TScreen extends ScreenResponseBase<TAction>,
  TAction extends ScreenActionDto<TPayload>,
  TActionId extends string,
  TState,
  TPayload extends Record<string, unknown>,
>({
  screen,
  actionId,
  editorState,
  findAction,
  buildPatch,
}: EditorActionRequestArgs<TScreen, TAction, TActionId, TState, TPayload>) {
  const action = findAction(screen, actionId)

  if (!action) {
    return null
  }

  if (!action.enabled) {
    throw new ScreenActionDisabledError(action)
  }

  const patch = buildPatch(action.id as TActionId, editorState)
  const body = action.payloadTemplate ? buildScreenActionPayload(action, patch) : undefined

  return {
    action,
    body,
  }
}

export async function invokeEditorScreenAction<
  TScreen extends ScreenResponseBase<TAction>,
  TAction extends ScreenActionDto<TPayload>,
  TActionId extends string,
  TState,
  TPayload extends Record<string, unknown>,
  TResponse,
>(
  args: EditorActionRequestArgs<TScreen, TAction, TActionId, TState, TPayload>,
) {
  const prepared = prepareEditorScreenActionRequest(args)

  if (!prepared) {
    return null
  }

  const response = await invokeScreenAction<TScreen, TResponse>(
    prepared.action,
    prepared.body === undefined ? undefined : { body: prepared.body },
  )

  return {
    ...prepared,
    response,
  }
}

export async function invokeEditorEntityActionAndRefresh<
  TScreen extends ScreenResponseBase<TAction>,
  TAction extends ScreenActionDto<TPayload>,
  TActionId extends string,
  TState,
  TPayload extends Record<string, unknown>,
  TResponse,
  TResourceId,
>({
  getResourceId,
  refreshScreen,
  ...args
}: EditorActionRequestArgs<TScreen, TAction, TActionId, TState, TPayload> & {
  getResourceId: (response: TResponse) => TResourceId
  refreshScreen: (resourceId: TResourceId, response: TResponse) => Promise<void>
}) {
  const result = await invokeEditorScreenAction<
    TScreen,
    TAction,
    TActionId,
    TState,
    TPayload,
    TResponse
  >(args)

  if (!result) {
    return null
  }

  const resourceId = getResourceId(result.response)
  await refreshScreen(resourceId, result.response)

  return {
    ...result,
    resourceId,
  }
}
