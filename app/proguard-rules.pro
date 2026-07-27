# Project-specific R8 rules belong here. The current dependencies publish their consumer rules, so
# the optimized release build intentionally needs no broad keep rule. CI builds the release bundle
# to catch future reflection, serialization, or resource-shrinking regressions.
