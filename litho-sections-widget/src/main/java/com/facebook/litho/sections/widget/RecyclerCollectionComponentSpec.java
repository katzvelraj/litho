/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho.sections.widget;

import static com.facebook.yoga.YogaAlign.FLEX_START;
import static com.facebook.yoga.YogaEdge.ALL;
import static com.facebook.yoga.YogaPositionType.ABSOLUTE;

import android.support.annotation.IdRes;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemAnimator;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.support.v7.widget.SnapHelper;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import com.facebook.litho.Column;
import com.facebook.litho.Component;
import com.facebook.litho.Component.ContainerBuilder;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.EventHandler;
import com.facebook.litho.StateValue;
import com.facebook.litho.TouchEvent;
import com.facebook.litho.Wrapper;
import com.facebook.litho.annotations.FromTrigger;
import com.facebook.litho.annotations.LayoutSpec;
import com.facebook.litho.annotations.OnCreateInitialState;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.annotations.OnEvent;
import com.facebook.litho.annotations.OnTrigger;
import com.facebook.litho.annotations.OnUpdateState;
import com.facebook.litho.annotations.Param;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.PropDefault;
import com.facebook.litho.annotations.ResType;
import com.facebook.litho.annotations.State;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.sections.BaseLoadEventsHandler;
import com.facebook.litho.sections.LoadEventsHandler;
import com.facebook.litho.sections.Section;
import com.facebook.litho.sections.SectionContext;
import com.facebook.litho.sections.SectionTree;
import com.facebook.litho.sections.SectionTree.Target;
import com.facebook.litho.sections.config.SectionsConfiguration;
import com.facebook.litho.widget.Binder;
import com.facebook.litho.widget.LithoRecylerView;
import com.facebook.litho.widget.PTRRefreshEvent;
import com.facebook.litho.widget.Recycler;
import com.facebook.litho.widget.RecyclerEventsController;
import com.facebook.litho.widget.StaggeredGridLayoutHelper;
import com.facebook.litho.widget.ViewportInfo;
import java.util.List;

/**
 * A {@link Component} that renders a {@link Recycler} backed by a {@link Section} tree.
 *
 * <p>This {@link Component} handles the loading events from the {@link Section} hierarchy and shows
 * the appropriate error,loading or empty {@link Component} passed in as props. If either the empty
 * or the error components are not passed in and the {@link RecyclerCollectionComponent} is in one
 * of these states it will simply not render anything.
 *
 * <p>The {@link RecyclerCollectionComponent} also exposes a {@link LoadEventsHandler} and a {@link
 * OnScrollListener} as {@link Prop}s so its users can receive events about the state of the loading
 * and about the state of the {@link Recycler} scrolling.
 *
 * <p>clipToPadding, clipChildren, itemDecoration, scrollBarStyle, horizontalPadding,
 * verticalPadding and recyclerViewId {@link Prop}s will be directly applied to the {@link Recycler}
 * component.
 *
 * <p>The {@link RecyclerCollectionEventsController} {@link Prop} is a way to sent commands to the
 * {@link RecyclerCollectionComponentSpec}, such as scrollTo(position) and refresh().
 */
@LayoutSpec
public class RecyclerCollectionComponentSpec {

  @PropDefault
  protected static final RecyclerConfiguration recyclerConfiguration =
      new ListRecyclerConfiguration();

  @PropDefault protected static final boolean nestedScrollingEnabled = true;
  @PropDefault protected static final int scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY;
  @PropDefault protected static final int recyclerViewId = View.NO_ID;
  @PropDefault protected static final int overScrollMode = View.OVER_SCROLL_ALWAYS;

  @PropDefault
  protected static final boolean asyncStateUpdates =
      SectionsConfiguration.sectionComponentsAsyncStateUpdates;

  @PropDefault static final ItemAnimator itemAnimator = new NoUpdateItemAnimator();

  @PropDefault
  protected static final boolean asyncPropUpdates =
      SectionsConfiguration.sectionComponentsAsyncPropUpdates;

  @PropDefault
  protected static final boolean setRootAsync =
      ComponentsConfiguration.setRootAsyncRecyclerCollectionComponent;

  @PropDefault static final boolean clipToPadding = true;
  @PropDefault static final boolean clipChildren = true;
  @PropDefault static final int refreshProgressBarColor = 0XFF4267B2; // blue
  private static final int MIN_SCROLL_FOR_PAGE = 20;

  @OnCreateLayout
  static Component onCreateLayout(
      final ComponentContext c,
      @Prop Section section,
      @Prop(optional = true) Component loadingComponent,
      @Prop(optional = true) Component emptyComponent,
      @Prop(optional = true) Component errorComponent,
      @Prop(optional = true, varArg = "onScrollListener") List<OnScrollListener> onScrollListeners,
      @Prop(optional = true) final LoadEventsHandler loadEventsHandler,
      @Prop(optional = true) boolean clipToPadding,
      @Prop(optional = true) boolean clipChildren,
      @Prop(optional = true) boolean nestedScrollingEnabled,
      @Prop(optional = true) int scrollBarStyle,
      @Prop(optional = true) ItemDecoration itemDecoration,
      @Prop(optional = true) ItemAnimator itemAnimator,
      @Prop(optional = true) @IdRes int recyclerViewId,
      @Prop(optional = true) int overScrollMode,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int leftPadding,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int rightPadding,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int topPadding,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int bottomPadding,
      @Prop(optional = true) EventHandler<TouchEvent> recyclerTouchEventHandler,
      @Prop(optional = true) boolean canMeasureRecycler,
      @Prop(optional = true) boolean horizontalFadingEdgeEnabled,
      @Prop(optional = true) boolean verticalFadingEdgeEnabled,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int fadingEdgeLength,
      @Prop(optional = true, resType = ResType.COLOR) int refreshProgressBarColor,
      @Prop(optional = true) LithoRecylerView.TouchInterceptor touchInterceptor,
      @Prop(optional = true) boolean setRootAsync,
      @Prop(optional = true) boolean disablePTR,
      @Prop(optional = true) RecyclerConfiguration recyclerConfiguration,
      @State(canUpdateLazily = true) boolean hasSetSectionTreeRoot,
      @State RecyclerCollectionEventsController internalEventsController,
      @State LoadingState loadingState,
      @State Binder<RecyclerView> binder,
      @State SectionTree sectionTree,
      @State RecyclerCollectionLoadEventsHandler recyclerCollectionLoadEventsHandler,
      @State SnapHelper snapHelper) {

    // This is a side effect from OnCreateLayout, so it's inherently prone to race conditions:
    recyclerCollectionLoadEventsHandler.setLoadEventsHandler(loadEventsHandler);

    // More side effects in OnCreateLayout. Watch out:
    if (hasSetSectionTreeRoot && setRootAsync) {
      sectionTree.setRootAsync(section);
    } else {
      RecyclerCollectionComponent.lazyUpdateHasSetSectionTreeRoot(c, true);
      sectionTree.setRoot(section);
    }

    final boolean isErrorButNoErrorComponent =
        loadingState == LoadingState.ERROR && (errorComponent == null);
    final boolean isEmptyButNoEmptyComponent =
        loadingState == LoadingState.EMPTY && (emptyComponent == null);
    final boolean shouldHideComponent = isEmptyButNoEmptyComponent || isErrorButNoErrorComponent;

    if (shouldHideComponent) {
      return null;
    }

    final boolean canPTR =
        recyclerConfiguration.getOrientation() != OrientationHelper.HORIZONTAL && !disablePTR;

    final Recycler.Builder recycler =
        Recycler.create(c)
            .clipToPadding(clipToPadding)
            .leftPadding(leftPadding)
            .rightPadding(rightPadding)
            .topPadding(topPadding)
            .bottomPadding(bottomPadding)
            .clipChildren(clipChildren)
            .nestedScrollingEnabled(nestedScrollingEnabled)
            .scrollBarStyle(scrollBarStyle)
            .recyclerViewId(recyclerViewId)
            .overScrollMode(overScrollMode)
            .recyclerEventsController(internalEventsController)
            .refreshHandler(!canPTR ? null : RecyclerCollectionComponent.onRefresh(c, sectionTree))
            .pullToRefresh(canPTR)
            .itemDecoration(itemDecoration)
            .canMeasure(canMeasureRecycler)
            .horizontalFadingEdgeEnabled(horizontalFadingEdgeEnabled)
            .verticalFadingEdgeEnabled(verticalFadingEdgeEnabled)
            .fadingEdgeLengthDip(fadingEdgeLength)
            .onScrollListener(new RecyclerCollectionOnScrollListener(internalEventsController))
            .onScrollListeners(onScrollListeners)
            .refreshProgressBarColor(refreshProgressBarColor)
            .snapHelper(snapHelper)
            .touchInterceptor(touchInterceptor)
            .binder(binder)
            .itemAnimator(
                RecyclerCollectionComponentSpec.itemAnimator == itemAnimator
                    ? new NoUpdateItemAnimator()
                    : itemAnimator)
            .flexShrink(0)
            .touchHandler(recyclerTouchEventHandler);

    if (!canMeasureRecycler && !recyclerConfiguration.isWrapContent()) {
      recycler.positionType(ABSOLUTE).positionPx(ALL, 0);
    }

    final ContainerBuilder containerBuilder =
        Column.create(c).flexShrink(0).alignContent(FLEX_START).child(recycler);

    if (loadingState == LoadingState.LOADING && loadingComponent != null) {
      containerBuilder.child(
          Wrapper.create(c)
              .delegate(loadingComponent)
              .flexShrink(0)
              .positionType(ABSOLUTE)
              .positionPx(ALL, 0));
    } else if (loadingState == LoadingState.EMPTY) {
      containerBuilder.child(
          Wrapper.create(c)
              .delegate(emptyComponent)
              .flexShrink(0)
              .positionType(ABSOLUTE)
              .positionPx(ALL, 0));
    } else if (loadingState == LoadingState.ERROR) {
      containerBuilder.child(
          Wrapper.create(c)
              .delegate(errorComponent)
              .flexShrink(0)
              .positionType(ABSOLUTE)
              .positionPx(ALL, 0));
    }

    return containerBuilder.build();
  }

  @OnCreateInitialState
  static <E extends Binder<RecyclerView> & Target> void createInitialState(
      final ComponentContext c,
      @Prop Section section,
      @Prop(optional = true) RecyclerConfiguration recyclerConfiguration,
      @Prop(optional = true) RecyclerCollectionEventsController eventsController,
      @Prop(optional = true) boolean asyncPropUpdates,
      @Prop(optional = true) boolean asyncStateUpdates,
      // NB: This is a *workaround* for sections that use non-threadsafe models, e.g. models that
      // may be updated from the main thread while background changesets would be calculated. It has
      // negative performance implications since it forces all changesets to be calculated on the
      // main thread!
      @Prop(optional = true) boolean forceSyncStateUpdates,
      // Caution: ignoreLoadingUpdates breaks loadingComponent/errorComponent/emptyComponent.
      // It's intended to be a temporary workaround, not something you should use often.
      @Prop(optional = true) boolean ignoreLoadingUpdates,
      @Prop(optional = true) String sectionTreeTag,
      StateValue<SnapHelper> snapHelper,
      StateValue<SectionTree> sectionTree,
      StateValue<RecyclerCollectionLoadEventsHandler> recyclerCollectionLoadEventsHandler,
      StateValue<Binder<RecyclerView>> binder,
      StateValue<LoadingState> loadingState,
      StateValue<RecyclerCollectionEventsController> internalEventsController) {

    E targetBinder = recyclerConfiguration.buildTarget(c);

    final SectionContext sectionContext = new SectionContext(c);
    binder.set(targetBinder);
    snapHelper.set(recyclerConfiguration.getSnapHelper());

    final SectionTree sectionTreeInstance =
        SectionTree.create(sectionContext, targetBinder)
            .tag(
                sectionTreeTag == null || sectionTreeTag.equals("")
                    ? section.getSimpleName()
                    : sectionTreeTag)
            .asyncPropUpdates(asyncPropUpdates)
            .asyncStateUpdates(asyncStateUpdates)
            .forceSyncStateUpdates(forceSyncStateUpdates)
            .build();
    sectionTree.set(sectionTreeInstance);

    final RecyclerCollectionEventsController internalEventsControllerInstance =
        eventsController != null ? eventsController : new RecyclerCollectionEventsController();
    internalEventsControllerInstance.setSectionTree(sectionTreeInstance);
    internalEventsController.set(internalEventsControllerInstance);

    final RecyclerCollectionLoadEventsHandler recyclerCollectionLoadEventsHandlerInstance =
        new RecyclerCollectionLoadEventsHandler(
            c, internalEventsControllerInstance, ignoreLoadingUpdates);
    recyclerCollectionLoadEventsHandler.set(recyclerCollectionLoadEventsHandlerInstance);
    sectionTreeInstance.setLoadEventsHandler(recyclerCollectionLoadEventsHandlerInstance);

    final ViewportInfo.ViewportChanged viewPortChanged =
        new ViewportInfo.ViewportChanged() {
          @Override
          public void viewportChanged(
              int firstVisibleIndex,
              int lastVisibleIndex,
              int firstFullyVisibleIndex,
              int lastFullyVisibleIndex,
              int state) {
            sectionTreeInstance.viewPortChanged(
                firstVisibleIndex,
                lastVisibleIndex,
                firstFullyVisibleIndex,
                lastFullyVisibleIndex,
                state);
          }
        };

    targetBinder.setViewportChangedListener(viewPortChanged);

    if (ignoreLoadingUpdates) {
      loadingState.set(LoadingState.LOADED);
    } else {
      loadingState.set(LoadingState.LOADING);
    }
  }

  @OnUpdateState
  static void updateLoadingState(
      StateValue<LoadingState> loadingState, @Param LoadingState currentLoadingState) {
    loadingState.set(currentLoadingState);
  }

  @OnEvent(PTRRefreshEvent.class)
  protected static void onRefresh(ComponentContext c, @Param SectionTree sectionTree) {
    sectionTree.refresh();
  }

  @OnTrigger(ScrollEvent.class)
  static void onScroll(
      ComponentContext c,
      @FromTrigger int position,
      @FromTrigger boolean animate,
      @State SectionTree sectionTree) {
    sectionTree.requestFocusOnRoot(position);
  }

  private static class RecyclerCollectionOnScrollListener extends OnScrollListener {

    private final RecyclerCollectionEventsController mEventsController;

    private RecyclerCollectionOnScrollListener(
        RecyclerCollectionEventsController eventsController) {
      mEventsController = eventsController;
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
      super.onScrolled(recyclerView, dx, dy);

      int firstCompletelyVisibleItemPosition =
          getFirstCompletelyVisibleItemPosition(recyclerView.getLayoutManager());
      if (firstCompletelyVisibleItemPosition != -1) {
        // firstCompletelyVisibleItemPosition can be -1 in middle of the scroll, so
        // wait until it finishes to set the state.
        mEventsController.setFirstCompletelyVisibleItemPosition(firstCompletelyVisibleItemPosition);
      }
    }

    private int getFirstCompletelyVisibleItemPosition(RecyclerView.LayoutManager layoutManager) {
      if (layoutManager instanceof StaggeredGridLayoutManager) {
        return StaggeredGridLayoutHelper.findFirstFullyVisibleItemPosition(
            (StaggeredGridLayoutManager) layoutManager);
      } else {
        return ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition();
      }
    }
  }

  enum LoadingState {
    /** We're loading but don't have any content yet. */
    LOADING,
    /** A load completed and we have content. */
    LOADED,
    /** A load completed, but the content is empty. */
    EMPTY,
    /** A load failed with an error. */
    ERROR,
  }

  static class RecyclerCollectionLoadEventsHandler extends BaseLoadEventsHandler {

    private LoadEventsHandler mDelegate;
    private LoadingState mLastState = LoadingState.LOADING;
    private final ComponentContext mComponentContext;
    private final RecyclerEventsController mRecyclerEventsController;
    private final boolean mIgnoreLoadingUpdates;

    private RecyclerCollectionLoadEventsHandler(
        ComponentContext c,
        RecyclerEventsController recyclerEventsController,
        boolean ignoreLoadingUpdates) {
      mComponentContext = c;
      mRecyclerEventsController = recyclerEventsController;
      mIgnoreLoadingUpdates = ignoreLoadingUpdates;
    }

    /** May be called from any thread (in OnCreateLayout). (Does this need synchronization?) */
    public void setLoadEventsHandler(LoadEventsHandler delegate) {
      mDelegate = delegate;
    }

    /**
     * One would hope this is only called from one thread, since onLoadSucceeded could arrive before
     * onLoadStarted if you post them on different threads. But use synchronized to defend against
     * bad clients.
     *
     * <p>This method exists to avoid thrashing Litho with state updates as we do a bunch of load
     * operations. In theory you could call updateLoadingStateAsync every single time and get the
     * same result, but it's more efficient to avoid all the unnecessary updates.
     */
    private synchronized void updateState(LoadingState newState) {
      if (mIgnoreLoadingUpdates) {
        return;
      }
      if (mLastState != newState) {
        mLastState = newState;
        RecyclerCollectionComponent.updateLoadingStateAsync(mComponentContext, newState);
      }
    }

    @Override
    public void onLoadStarted(boolean empty) {
      updateState(empty ? LoadingState.LOADING : LoadingState.LOADED);

      final LoadEventsHandler delegate = mDelegate;
      if (delegate != null) {
        delegate.onLoadStarted(empty);
      }
    }

    @Override
    public void onLoadSucceeded(boolean empty) {
      updateState(empty ? LoadingState.EMPTY : LoadingState.LOADED);

      mRecyclerEventsController.clearRefreshing();

      final LoadEventsHandler delegate = mDelegate;
      if (delegate != null) {
        delegate.onLoadSucceeded(empty);
      }
    }

    @Override
    public void onLoadFailed(boolean empty) {
      updateState(empty ? LoadingState.ERROR : LoadingState.LOADED);

      mRecyclerEventsController.clearRefreshing();

      final LoadEventsHandler delegate = mDelegate;
      if (delegate != null) {
        delegate.onLoadFailed(empty);
      }
    }

    @Override
    public void onInitialLoad() {
      final LoadEventsHandler delegate = mDelegate;
      if (delegate != null) {
        delegate.onInitialLoad();
      }
    }
  }

  public static class NoUpdateItemAnimator extends DefaultItemAnimator {

    public NoUpdateItemAnimator() {
      super();
      setSupportsChangeAnimations(false);
    }
  }
}
