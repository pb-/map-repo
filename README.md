# map-repo

map-repo applies a command to a sequence of Github repositories and turns changes into pull requests.

## Example

```sh
# Delete all README.md files in repositories matching the pattern core-.* in the my-organization organization
GITHUB_TOKEN=abc123 clojure -m map-repo.core --org my-organization --pattern 'core-.*' --message 'Deleting useless readmes' rm README.md
```

## TODO

 * Replace git shell calls with a proper library
 * Add web Oauth2 flow instead of token auth
 * Clean up local temporary directory
