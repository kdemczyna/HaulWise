import { Button } from "@mantine/core";
import { Link } from "react-router";
import { EmptyState } from "../components/common/EmptyState.jsx";
import { PageHeader } from "../components/common/PageHeader.jsx";

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" />
      <EmptyState
        title="There is nothing at this address"
        message="The link may be mistyped or the page may have moved."
        action={
          <Button component={Link} to="/">
            Go to Plan a run
          </Button>
        }
      />
    </>
  );
}
