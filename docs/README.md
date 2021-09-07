# Java SDK docs


## Building docs

To build the docs, run `make` in the `docs` directory:

```
make
```

Dynamically-generated and managed sources will be created in `build/src/managed`.

### Iterating

After doing a full build with 'make', you can use more specific make targets to
speed up iterating:

* only rebuild doc content with `make dev-html`
* copy the samples and rebuild docs with `make examples dev-html`

## Deploying docs

Docs are automatically published on releases. To deploy the docs manually run:

```
make deploy
```
