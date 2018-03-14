/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.graph.hook;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;

import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition;
import uk.gov.gchq.gaffer.exception.SerialisationException;
import uk.gov.gchq.gaffer.jsonserialisation.JSONSerialiser;
import uk.gov.gchq.gaffer.operation.Operation;
import uk.gov.gchq.gaffer.operation.OperationChain;
import uk.gov.gchq.gaffer.operation.graph.OperationView;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.user.User;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * This is a hook to update the View for an operation, that correctly implements {@link OperationView}.
 * This hook will have no effect with operations that do not correctly implement {@link OperationView} therefore <b>THIS CLASS SHOULD NOT BE USED TO ENFORCE ROLE BASED FILTERING</b>.
 * Instead please make use of the visibility property ({@link uk.gov.gchq.gaffer.store.schema.Schema#visibilityProperty}).
 * <p>
 * All fields are Optional:
 * <ul>
 * <li>withOpAuth = Apply this hook to Users with <b>any</b> of these OpAuths</li>
 * <li>withoutOpAuth = Apply this hook to Users <b>without any</b> of these OpAuths, overrides withOpAuth</li>
 * <li>withDataAuth = Apply this hook to Users with <b>any</b> of these DataAuths</li>
 * <li>withoutDataAuth = Apply this hook to Users <b>without any</b> of these DataAuths, overrides withDataAuth</li>
 * <li>whiteListElementGroups = When this hook is applied, only allow View Element Groups from this list, overrides viewToMerge</li>
 * <li>blackListElementGroups = When this hook is applied, remove all View Element Groups from this list, overrides whiteListElementGroups and viewToMerge</li>
 * <li>viewToMerge = the view to be merged into the current view.</li>
 * </ul>
 *
 * @see GraphHook
 * @see uk.gov.gchq.gaffer.store.schema.Schema#visibilityProperty
 *
 */
public class UpdateViewHook implements GraphHook {

    private Set<String> withOpAuth;
    private Set<String> withoutOpAuth;
    private Set<String> withDataAuth;
    private Set<String> withoutDataAuth;
    private Set<String> whiteListElementGroups;
    private Set<String> blackListElementGroups;
    private byte[] viewToMerge;

    @Override
    public void preExecute(final OperationChain<?> opChain, final Context context) {
        if (applyToUser(context.getUser())) {
            updateView(opChain);
        }
    }

    private void updateView(final OperationChain<?> opChain) {
        for (final Operation operation : opChain.flatten()) {
            if (operation instanceof OperationView) {
                OperationView operationView = (OperationView) operation;

                View.Builder viewBuilder = mergeView(operationView, getViewToMerge());

                View originalView = operationView.getView();
                if (null != originalView) {
                    originalView.expandGlobalDefinitions();
                    if ((null != whiteListElementGroups && !whiteListElementGroups.isEmpty())
                            || (null != blackListElementGroups && !blackListElementGroups.isEmpty())) {

                        Map<String, ViewElementDefinition> entities = Maps.newHashMap(originalView.getEntities());
                        Set<Entry<String, ViewElementDefinition>> entitiesEntrySet = entities.entrySet();
                        entitiesEntrySet.removeIf(this::removeElementGroups);
                        viewBuilder.entities(entities);

                        Map<String, ViewElementDefinition> edges = Maps.newHashMap(originalView.getEdges());
                        Set<Entry<String, ViewElementDefinition>> edgesEntrySet = edges.entrySet();
                        edgesEntrySet.removeIf(this::removeElementGroups);
                        viewBuilder.edges(edges);
                    }
                }
                operationView.setView(viewBuilder.build());
            }
        }
    }

    protected final View.Builder mergeView(final OperationView operationView, final View viewToMerge) {
        View.Builder viewBuilder = new View.Builder()
                .merge(operationView.getView());

        if (null != viewToMerge) {
            viewBuilder.merge(viewToMerge.clone());
        }

        return viewBuilder;
    }

    protected final boolean removeElementGroups(final Entry<String, ViewElementDefinition> entry) {
        boolean remove;

        if (null != whiteListElementGroups) {
            remove = !whiteListElementGroups.contains(entry.getKey());
        } else {
            remove = false;
        }

        if (!remove) {
            if (null != blackListElementGroups) {
                remove = blackListElementGroups.contains(entry.getKey());
            }
        }
        return remove;
    }

    protected final boolean applyToUser(final User user) {
        return userContainsWithDataAuth(user)
                && userContainsWithOpAuth(user)
                && userNotContainsWithoutDataAuth(user)
                && userNotContainsWithoutOpAuth(user);
    }

    private boolean userNotContainsWithoutOpAuth(final User user) {
        return !validateAuths(user.getOpAuths(), this.withoutOpAuth, false);
    }

    private boolean userNotContainsWithoutDataAuth(final User user) {
        return !validateAuths(user.getDataAuths(), this.withoutDataAuth, false);
    }

    private boolean userContainsWithOpAuth(final User user) {
        return validateAuths(user.getOpAuths(), this.withOpAuth, true);
    }

    private boolean userContainsWithDataAuth(final User user) {
        return validateAuths(user.getDataAuths(), this.withDataAuth, true);
    }

    protected final boolean validateAuths(final Set<String> userAuths, final Set<String> validAuth, final boolean ifValidAuthIsNull) {
        boolean rtn = ifValidAuthIsNull;
        if (null != validAuth) {
            if (null == userAuths) {
                rtn = validAuth.isEmpty();
            } else {
                rtn = CollectionUtils.containsAny(userAuths, validAuth);
            }
        }
        return rtn;
    }

    @Override
    public <T> T postExecute(final T result, final OperationChain<?> opChain, final Context context) {
        return result;
    }

    @Override
    public <T> T onFailure(final T result, final OperationChain<?> opChain, final Context context, final Exception e) {
        return result;
    }

    @JsonGetter("withOpAuth")
    public Set<String> getWithOpAuth() {
        return withOpAuth;
    }

    @JsonSetter("withOpAuth")
    public UpdateViewHook setWithOpAuth(final Set<String> withOpAuth) {
        this.withOpAuth = withOpAuth;
        return this;
    }

    @JsonGetter("withoutOpAuth")
    public Set<String> getWithoutOpAuth() {
        return withoutOpAuth;
    }

    @JsonSetter("withoutOpAuth")
    public UpdateViewHook setWithoutOpAuth(final Set<String> withoutOpAuth) {
        this.withoutOpAuth = withoutOpAuth;
        return this;
    }

    @JsonGetter("withDataAuth")
    public Set<String> getWithDataAuth() {
        return withDataAuth;
    }

    @JsonSetter("withDataAuth")
    public UpdateViewHook setWithDataAuth(final Set<String> withDataAuth) {
        this.withDataAuth = withDataAuth;
        return this;
    }

    @JsonGetter("withoutDataAuth")
    public Set<String> getWithoutDataAuth() {
        return withoutDataAuth;
    }

    @JsonSetter("withoutDataAuth")
    public UpdateViewHook setWithoutDataAuth(final Set<String> withoutDataAuth) {
        this.withoutDataAuth = withoutDataAuth;
        return this;
    }

    @JsonGetter("whiteListElementGroups")
    public Set<String> getWhiteListElementGroups() {
        return whiteListElementGroups;
    }

    @JsonSetter("whiteListElementGroups")
    public UpdateViewHook setWhiteListElementGroups(final Set<String> whiteListElementGroups) {
        this.whiteListElementGroups = whiteListElementGroups;
        return this;
    }

    @JsonGetter("blackListElementGroups")
    public Set<String> getBlackListElementGroups() {
        return blackListElementGroups;
    }

    @JsonSetter("blackListElementGroups")
    public UpdateViewHook setBlackListElementGroups(final Set<String> blackListElementGroups) {
        this.blackListElementGroups = blackListElementGroups;
        return this;
    }


    @JsonSetter("viewToMerge")
    public UpdateViewHook setViewToMerge(final View viewToMerge) {
        this.viewToMerge = getViewToMerge(viewToMerge);
        return this;
    }

    private static byte[] getViewToMerge(final View viewToMerge) {
        byte[] serialise = null;
        if (null != viewToMerge) {
            try {
                serialise = JSONSerialiser.serialise(viewToMerge, true);
            } catch (final SerialisationException e) {
                throw new RuntimeException("Could not serialise the viewToMerge", e);
            }
        }
        return serialise;
    }

    @JsonGetter("viewToMerge")
    public View getViewToMerge() {
        return getViewToMerge(viewToMerge);
    }

    private static View getViewToMerge(final byte[] viewToMerge) {
        View deserialise;
        if (null != viewToMerge) {
            try {
                deserialise = JSONSerialiser.deserialise(viewToMerge, View.class);
            } catch (final SerialisationException e) {
                throw new RuntimeException("Could not deserialise viewToMerge", e);
            }
        } else {
            deserialise = null;
        }
        return deserialise;
    }

    public static class Builder {
        private Set<String> withOpAuth;
        private Set<String> withoutOpAuth;
        private Set<String> withDataAuth;
        private Set<String> withoutDataAuth;
        private Set<String> whiteListElementGroups;
        private Set<String> blackListElementGroups;
        private byte[] viewToMerge;

        public Builder withOpAuth(final Set<String> withOpAuth) {
            this.withOpAuth = withOpAuth;
            return this;
        }

        public Builder withoutOpAuth(final Set<String> withoutOpAuth) {
            this.withoutOpAuth = withoutOpAuth;
            return this;
        }

        public Builder withDataAuth(final Set<String> withDataAuths) {
            this.withDataAuth = withDataAuths;
            return this;
        }

        public Builder withoutDataAuth(final Set<String> withoutDataAuth) {
            this.withoutDataAuth = withoutDataAuth;
            return this;
        }

        public Builder whiteListElementGroups(final Set<String> whiteListElementGroups) {
            this.whiteListElementGroups = whiteListElementGroups;
            return this;
        }

        public Builder blackListElementGroups(final Set<String> blackListElementGroups) {
            this.blackListElementGroups = blackListElementGroups;
            return this;
        }

        public Builder setViewToMerge(final View viewToMerge) {
            this.viewToMerge = UpdateViewHook.getViewToMerge(viewToMerge);
            return this;
        }

        private View getViewToMerge() {
            return UpdateViewHook.getViewToMerge(viewToMerge);

        }

        public UpdateViewHook build() {
            return new UpdateViewHook()
                    .setWithOpAuth(withOpAuth)
                    .setWithoutOpAuth(withoutOpAuth)
                    .setWithDataAuth(withDataAuth)
                    .setWithoutDataAuth(withoutDataAuth)
                    .setWhiteListElementGroups(whiteListElementGroups)
                    .setBlackListElementGroups(blackListElementGroups)
                    .setViewToMerge(getViewToMerge());
        }
    }

}
